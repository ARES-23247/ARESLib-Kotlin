package com.areslib.telemetry

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Global tracker for robot runtime status, exposed to the web portal over Wi-Fi.
 */
object RobotStatusTracker {
    @Volatile
    var isEnabled: Boolean = false

    @Volatile
    var activeOpMode: String = "Disabled"
}

/**
 * Embedded HTTP Server running on the robot (REV Control Hub or RoboRIO).
 * Listens on port 8082 by default, serving state queries and log uploads to the web app.
 */
object RobotWebServer {
    private var server: HttpServer? = null
    private val executor = Executors.newSingleThreadExecutor { thread ->
        Thread(thread, "ARES-WebServer-Thread").apply { isDaemon = true }
    }

    private val logDir: File by lazy {
        val javaVendor = System.getProperty("java.vendor") ?: ""
        val isAndroid = javaVendor.contains("Android", ignoreCase = true) || File("/sdcard").exists()
        if (isAndroid) File("/sdcard/FIRST/telemetry_logs/") else File("./logs/")
    }

    /**
     * Starts the web server in a background thread.
     */
    fun start(port: Int = 8082) {
        if (server != null) return
        try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/api/status", StatusHandler())
                createContext("/api/logs", LogsHandler())
                createContext("/api/logs/download", DownloadHandler())
                createContext("/api/logs/markSynced", MarkSyncedHandler())
                executor = this@RobotWebServer.executor
                start()
            }
            println("ARES Robot WebServer started successfully on port $port")
        } catch (e: Exception) {
            System.err.println("ARES Robot WebServer: Failed to start on port $port! ${e.message}")
        }
    }

    /**
     * Stops the web server.
     */
    fun stop() {
        server?.stop(0)
        server = null
        executor.shutdown()
    }

    private class StatusHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            enableCors(exchange)
            if ("GET" != exchange.requestMethod) {
                sendError(exchange, 405, "Method Not Allowed")
                return
            }

            val response = """{
                "enabled": ${RobotStatusTracker.isEnabled},
                "opMode": "${RobotStatusTracker.activeOpMode}"
            }""".trimIndent()

            sendJson(exchange, response)
        }
    }

    private class LogsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            enableCors(exchange)
            if ("GET" != exchange.requestMethod) {
                sendError(exchange, 405, "Method Not Allowed")
                return
            }

            if (!logDir.exists()) {
                sendJson(exchange, "[]")
                return
            }

            val files = logDir.listFiles { _, name -> name.endsWith(".csv") } ?: emptyArray()
            val fileList = files.sortedBy { it.name }.joinToString(",") { "\"${it.name}\"" }
            sendJson(exchange, "[$fileList]")
        }
    }

    private class DownloadHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            enableCors(exchange)
            if ("GET" != exchange.requestMethod) {
                sendError(exchange, 405, "Method Not Allowed")
                return
            }

            val query = exchange.requestURI.query ?: ""
            val fileName = query.split("=").getOrNull(1)
            if (fileName == null) {
                sendError(exchange, 400, "Bad Request: Missing 'file' parameter")
                return
            }

            val file = File(logDir, fileName)
            if (!file.exists() || !file.name.endsWith(".csv")) {
                sendError(exchange, 404, "Log file not found")
                return
            }

            exchange.responseHeaders.set("Content-Type", "text/csv")
            exchange.sendResponseHeaders(200, file.length())

            val outputStream: OutputStream = exchange.responseBody
            val fileInputStream = FileInputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()
            outputStream.close()
        }
    }

    private class MarkSyncedHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            enableCors(exchange)
            if ("POST" != exchange.requestMethod && "OPTIONS" != exchange.requestMethod) {
                sendError(exchange, 405, "Method Not Allowed")
                return
            }

            if ("OPTIONS" == exchange.requestMethod) {
                exchange.sendResponseHeaders(204, -1)
                return
            }

            val query = exchange.requestURI.query ?: ""
            val fileName = query.split("=").getOrNull(1)
            if (fileName == null) {
                sendError(exchange, 400, "Bad Request: Missing 'file' parameter")
                return
            }

            val file = File(logDir, fileName)
            if (!file.exists() || !file.name.endsWith(".csv")) {
                sendError(exchange, 404, "Log file not found")
                return
            }

            val syncedDir = File(logDir, "synced")
            if (!syncedDir.exists()) {
                syncedDir.mkdirs()
            }

            val destFile = File(syncedDir, file.name)
            val success = file.renameTo(destFile)

            if (success) {
                sendJson(exchange, "{\"success\": true, \"message\": \"Marked file ${file.name} as synced\"}")
            } else {
                sendError(exchange, 500, "Failed to archive log file")
            }
        }
    }

    private fun enableCors(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            set("Access-Control-Allow-Origin", "*")
            set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            set("Access-Control-Allow-Headers", "Content-Type, Authorization")
        }
    }

    private fun sendJson(exchange: HttpExchange, json: String) {
        val bytes = json.toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        val os: OutputStream = exchange.responseBody
        os.write(bytes)
        os.close()
    }

    private fun sendError(exchange: HttpExchange, code: Int, message: String) {
        val bytes = "{\"error\": \"$message\"}".toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        val os: OutputStream = exchange.responseBody
        os.write(bytes)
        os.close()
    }
}
