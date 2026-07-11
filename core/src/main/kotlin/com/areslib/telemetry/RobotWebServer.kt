package com.areslib.telemetry

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.CopyOnWriteArrayList

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

    @Volatile
    var uploadProgress: Double = 0.0

    @Volatile
    var activeUploadFile: String? = null
}

/**
 * Embedded HTTP Server running on the robot (REV Control Hub or RoboRIO).
 * Listens on port 8082 by default, serving state queries and log uploads to the web app.
 * Built using pure Java Socket and ServerSocket to run on Android (REV Control Hub)
 * where com.sun.net.httpserver is unavailable.
 */
object RobotWebServer {
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var discoveryThread: Thread? = null
    private val activeForwarders = CopyOnWriteArrayList<PortForwarder>()
    private var executor: ExecutorService? = null

    private val logDir: File by lazy {
        val javaVendor = System.getProperty("java.vendor") ?: ""
        val isAndroid = javaVendor.contains("Android", ignoreCase = true) || File("/sdcard").exists()
        if (isAndroid) File("/sdcard/FIRST/telemetry_logs/") else File("./logs/")
    }

    /**
     * Starts the web server in a background thread.
     */
    @Synchronized
    fun start(port: Int = 8082) {
        if (serverSocket != null) return
        try {
            if (executor == null || executor!!.isShutdown) {
                executor = Executors.newCachedThreadPool { thread ->
                    Thread(thread, "ARES-WebServer-Worker").apply { isDaemon = true }
                }
            }

            serverSocket = ServerSocket(port)
            val socket = serverSocket!!

            serverThread = Thread({
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val client = socket.accept()
                        executor?.submit {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }, "ARES-WebServer-Acceptor").apply {
                isDaemon = true
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
                            val testSocket = Socket()
                            testSocket.connect(InetSocketAddress(ip, 5800), 150)
                            testSocket.close()
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
    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        serverThread?.interrupt()
        serverThread = null

        discoveryThread?.interrupt()
        discoveryThread = null

        for (f in activeForwarders) {
            f.stopForwarder()
        }
        activeForwarders.clear()

        executor?.shutdownNow()
        executor = null
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
            val firstLine = reader.readLine() ?: return

            val tokens = firstLine.split(" ")
            if (tokens.size < 2) {
                sendErrorResponse(client, 400, "Bad Request")
                return
            }
            val method = tokens[0]
            val fullPath = tokens[1]

            // Read headers until blank line
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine()
                if (line == null || line.trim().isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            if (method == "OPTIONS") {
                sendOptionsResponse(client)
                return
            }

            val questionIdx = fullPath.indexOf('?')
            val path = if (questionIdx != -1) fullPath.substring(0, questionIdx) else fullPath
            val query = if (questionIdx != -1) fullPath.substring(questionIdx + 1) else ""

            when (path) {
                "/api/status" -> handleStatus(client, method, headers)
                "/api/logs" -> handleLogs(client, method)
                "/api/logs/download" -> handleDownload(client, method, query)
                "/api/logs/markSynced" -> handleMarkSynced(client, method, query)
                "/api/limelight/stream" -> handleLimelightStream(client, method)
                else -> sendErrorResponse(client, 404, "Not Found")
            }
        } catch (e: Exception) {
            // Socket or parsing error
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun writeCorsHeaders(out: OutputStream) {
        out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
        out.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n".toByteArray())
        out.write("Access-Control-Allow-Headers: Content-Type, Authorization\r\n".toByteArray())
        out.write("Access-Control-Allow-Private-Network: true\r\n".toByteArray())
    }

    private fun sendOptionsResponse(client: Socket) {
        val out = client.getOutputStream()
        out.write("HTTP/1.1 204 No Content\r\n".toByteArray())
        writeCorsHeaders(out)
        out.write("Connection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun sendJsonResponse(client: Socket, status: Int, statusText: String, json: String) {
        val out = client.getOutputStream()
        val bytes = json.toByteArray(Charsets.UTF_8)
        out.write("HTTP/1.1 $status $statusText\r\n".toByteArray())
        writeCorsHeaders(out)
        out.write("Content-Type: application/json\r\n".toByteArray())
        out.write("Content-Length: ${bytes.size}\r\n".toByteArray())
        out.write("Connection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun sendErrorResponse(client: Socket, code: Int, message: String) {
        sendJsonResponse(client, code, message, "{\"error\": \"$message\"}")
    }

    private fun handleStatus(client: Socket, method: String, headers: Map<String, String>) {
        if (method != "GET") {
            sendErrorResponse(client, 405, "Method Not Allowed")
            return
        }

        val host = headers["host"] ?: "localhost:8082"
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

        val activeFileJson = if (RobotStatusTracker.activeUploadFile != null) "\"${RobotStatusTracker.activeUploadFile}\"" else "null"
        val response = """{
            "enabled": ${RobotStatusTracker.isEnabled},
            "opMode": "${RobotStatusTracker.activeOpMode}",
            "vision": {
                "connected": ${RobotStatusTracker.visionConnected},
                "status": "${RobotStatusTracker.visionStatus}",
                "cameras": [$camerasJson]
            },
            "upload": {
                "activeFile": $activeFileJson,
                "progress": ${RobotStatusTracker.uploadProgress}
            }
        }""".trimIndent()

        sendJsonResponse(client, 200, "OK", response)
    }

    private fun handleLogs(client: Socket, method: String) {
        if (method != "GET") {
            sendErrorResponse(client, 405, "Method Not Allowed")
            return
        }

        if (!logDir.exists()) {
            sendJsonResponse(client, 200, "OK", "[]")
            return
        }

        val files = logDir.listFiles { _, name -> name.endsWith(".csv") || name.endsWith(".jsonl") } ?: emptyArray()
        val fileList = files.sortedBy { it.name }.joinToString(",") { "\"${it.name}\"" }
        sendJsonResponse(client, 200, "OK", "[$fileList]")
    }

    private fun handleDownload(client: Socket, method: String, query: String) {
        if (method != "GET") {
            sendErrorResponse(client, 405, "Method Not Allowed")
            return
        }

        val fileName = query.split("&").firstOrNull { it.startsWith("file=") }?.substringAfter("file=")
        if (fileName == null) {
            sendErrorResponse(client, 400, "Bad Request: Missing 'file' parameter")
            return
        }

        val file = File(logDir, fileName)
        if (!file.exists() || !(file.name.endsWith(".csv") || file.name.endsWith(".jsonl"))) {
            sendErrorResponse(client, 404, "Log file not found")
            return
        }

        val out = client.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\n".toByteArray())
        writeCorsHeaders(out)
        val contentType = if (file.name.endsWith(".jsonl")) "application/x-jsonlines" else "text/csv"
        out.write("Content-Type: $contentType\r\n".toByteArray())
        out.write("Content-Length: ${file.length()}\r\n".toByteArray())
        out.write("Connection: close\r\n\r\n".toByteArray())

        FileInputStream(file).use { fis ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
        }
        out.flush()
    }

    private fun handleMarkSynced(client: Socket, method: String, query: String) {
        if (method != "POST") {
            sendErrorResponse(client, 405, "Method Not Allowed")
            return
        }

        val fileName = query.split("&").firstOrNull { it.startsWith("file=") }?.substringAfter("file=")
        if (fileName == null) {
            sendErrorResponse(client, 400, "Bad Request: Missing 'file' parameter")
            return
        }

        val file = File(logDir, fileName)
        if (!file.exists() || !(file.name.endsWith(".csv") || file.name.endsWith(".jsonl"))) {
            sendErrorResponse(client, 404, "Log file not found")
            return
        }

        val syncedDir = File(logDir, "synced")
        if (!syncedDir.exists()) {
            syncedDir.mkdirs()
        }

        val destFile = File(syncedDir, file.name)
        val success = file.renameTo(destFile)

        if (success) {
            sendJsonResponse(client, 200, "OK", "{\"success\": true, \"message\": \"Marked file ${file.name} as synced\"}")
        } else {
            sendErrorResponse(client, 500, "Failed to archive log file")
        }
    }

    private fun handleLimelightStream(client: Socket, method: String) {
        if (method != "GET") {
            sendErrorResponse(client, 405, "Method Not Allowed")
            return
        }

        val ip = RobotStatusTracker.resolvedLimelightIp ?: RobotStatusTracker.activeLimelightIps.firstOrNull()
        if (ip == null) {
            sendErrorResponse(client, 502, "Bad Gateway: Limelight camera not found on USB tether subnets")
            return
        }

        val limelightUrl = java.net.URL("http://$ip:5800/stream.mjpeg")
        var headersSent = false
        try {
            val connection = limelightUrl.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1000
            connection.readTimeout = 15000

            val out = client.getOutputStream()
            out.write("HTTP/1.1 ${connection.responseCode} OK\r\n".toByteArray())
            writeCorsHeaders(out)

            // Copy response headers
            for ((headerKey, headerValues) in connection.headerFields) {
                if (headerKey != null && headerKey.lowercase() != "connection") {
                    for (value in headerValues) {
                        out.write("$headerKey: $value\r\n".toByteArray())
                    }
                }
            }
            out.write("Connection: close\r\n\r\n".toByteArray())
            out.flush()
            headersSent = true

            val input = connection.inputStream
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                out.flush()
            }
            input.close()
        } catch (e: Exception) {
            RobotStatusTracker.resolvedLimelightIp = null
            if (!headersSent) {
                sendErrorResponse(client, 502, "Bad Gateway: Failed to stream from Limelight at $ip:5800. Error: ${e.message}")
            }
        }
    }

    private class PortForwarder(private val localPort: Int, private val remotePort: Int, private val targetIp: String) : Thread("ARES-PortForwarder-$localPort") {
        private var serverSocket: java.net.ServerSocket? = null
        @Volatile private var running = true

        init {
            isDaemon = true
        }

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
                            }.apply { isDaemon = true }.start()
                            try {
                                serverSocketConnection.inputStream.copyTo(clientSocket.outputStream)
                            } catch (_: Exception) {} finally {
                                try { serverSocketConnection.close() } catch (_: Exception) {}
                                try { clientSocket.close() } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }.apply { isDaemon = true }.start()
                }
            } catch (_: Exception) {}
        }

        fun stopForwarder() {
            running = false
            try { serverSocket?.close() } catch (_: Exception) {}
        }
    }
}
