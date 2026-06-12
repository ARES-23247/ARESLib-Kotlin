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

    @Volatile
    var visionConnected: Boolean = false

    @Volatile
    var visionStatus: String = "OFFLINE"

    @Volatile
    var resolvedLimelightIp: String? = null

    @Volatile
    var activeLimelightIps: List<String> = emptyList()
}

/**
 * Embedded HTTP Server running on the robot (REV Control Hub or RoboRIO).
 * Listens on port 8082 by default, serving state queries and log uploads to the web app.
 */
object RobotWebServer {
    private var server: HttpServer? = null
    private var discoveryThread: Thread? = null
    private val activeForwarders = java.util.concurrent.CopyOnWriteArrayList<PortForwarder>()
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
                createContext("/api/limelight/stream", LimelightStreamHandler())
                executor = this@RobotWebServer.executor
                start()
            }

            // Start discovery thread to scan for active Limelight cameras and configure port forwards
            discoveryThread = Thread {
                val possibleIps = listOf("172.29.11.7", "172.22.11.2", "limelight.local", "172.29.11.2", "172.29.11.8", "172.29.11.9", "172.22.11.3")
                var lastIps = emptyList<String>()

                while (!Thread.currentThread().isInterrupted) {
                    val currentIps = mutableListOf<String>()
                    for (ip in possibleIps) {
                        try {
                            val socket = java.net.Socket()
                            socket.connect(java.net.InetSocketAddress(ip, 5800), 150)
                            socket.close()
                            currentIps.add(ip)
                        } catch (_: Exception) {}
                    }

                    if (currentIps != lastIps) {
                        // Stop old forwarders
                        for (f in activeForwarders) {
                            f.stopForwarder()
                        }
                        activeForwarders.clear()

                        // Start new forwarders
                        for ((index, ip) in currentIps.withIndex()) {
                            val basePort = 5800 + (index * 2)
                            activeForwarders.add(PortForwarder(basePort, 5800, ip).apply { start() })
                            activeForwarders.add(PortForwarder(basePort + 1, 5801, ip).apply { start() })
                        }
                        lastIps = currentIps
                        RobotStatusTracker.activeLimelightIps = currentIps
                    }

                    try {
                        Thread.sleep(5000)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }.apply {
                isDaemon = true
                name = "ARES-LimelightDiscovery-Thread"
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
        discoveryThread?.interrupt()
        discoveryThread = null
        for (f in activeForwarders) {
            f.stopForwarder()
        }
        activeForwarders.clear()
        executor.shutdown()
    }

    private class StatusHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            enableCors(exchange)
            if ("GET" != exchange.requestMethod) {
                sendError(exchange, 405, "Method Not Allowed")
                return
            }

            val host = exchange.requestHeaders.getFirst("Host") ?: "localhost:8082"
            val hostIp = host.split(":").firstOrNull() ?: "localhost"
            val camerasJson = RobotStatusTracker.activeLimelightIps.mapIndexed { index, ip ->
                val streamPort = 5800 + (index * 2)
                val configPort = 5801 + (index * 2)
                """{
                    "name": "limelight-${index + 1}",
                    "ip": "$ip",
                    "streamUrl": "http://$hostIp:$streamPort",
                    "configUrl": "http://$hostIp:$configPort"
                }"""
            }.joinToString(",")

            val response = """{
                "enabled": ${RobotStatusTracker.isEnabled},
                "opMode": "${RobotStatusTracker.activeOpMode}",
                "vision": {
                    "connected": ${RobotStatusTracker.visionConnected},
                    "status": "${RobotStatusTracker.visionStatus}",
                    "cameras": [$camerasJson]
                }
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

    private class LimelightStreamHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            enableCors(exchange)
            if ("GET" != exchange.requestMethod && "OPTIONS" != exchange.requestMethod) {
                sendError(exchange, 405, "Method Not Allowed")
                return
            }

            if ("OPTIONS" == exchange.requestMethod) {
                exchange.sendResponseHeaders(204, -1)
                return
            }

            val ip = RobotStatusTracker.resolvedLimelightIp ?: RobotStatusTracker.activeLimelightIps.firstOrNull()
            if (ip == null) {
                sendError(exchange, 502, "Bad Gateway: Limelight camera not found on USB tether subnets")
                return
            }

            val limelightUrl = java.net.URL("http://$ip:5800/stream.mjpeg")
            try {
                val connection = limelightUrl.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1000
                connection.readTimeout = 5000

                // Copy response headers
                for ((headerKey, headerValues) in connection.headerFields) {
                    if (headerKey != null) {
                        for (value in headerValues) {
                            exchange.responseHeaders.add(headerKey, value)
                        }
                    }
                }

                val responseCode = connection.responseCode
                exchange.sendResponseHeaders(responseCode, 0) // Chunked streaming

                val input = connection.inputStream
                val output = exchange.responseBody
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                }
                input.close()
                output.close()
            } catch (e: Exception) {
                RobotStatusTracker.resolvedLimelightIp = null
                sendError(exchange, 502, "Bad Gateway: Failed to stream from Limelight at $ip:5800. Error: ${e.message}")
            }
        }
    }

    private class PortForwarder(private val localPort: Int, private val remotePort: Int, private val targetIp: String) : Thread("ARES-PortForwarder-$localPort") {
        private var serverSocket: java.net.ServerSocket? = null
        @Volatile private var running = true

        override fun run() {
            try {
                serverSocket = java.net.ServerSocket(localPort)
                while (running) {
                    val clientSocket = serverSocket?.accept() ?: break
                    Thread {
                        try {
                            val serverSocketConnection = java.net.Socket(targetIp, remotePort)
                            Thread {
                                try {
                                    clientSocket.inputStream.copyTo(serverSocketConnection.outputStream)
                                } catch (_: Exception) {} finally {
                                    try { serverSocketConnection.close() } catch (_: Exception) {}
                                    try { clientSocket.close() } catch (_: Exception) {}
                                }
                            }.start()
                            try {
                                serverSocketConnection.inputStream.copyTo(clientSocket.outputStream)
                            } catch (_: Exception) {} finally {
                                try { serverSocketConnection.close() } catch (_: Exception) {}
                                try { clientSocket.close() } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }.start()
                }
            } catch (_: Exception) {}
        }

        fun stopForwarder() {
            running = false
            try { serverSocket?.close() } catch (_: Exception) {}
        }
    }
}
