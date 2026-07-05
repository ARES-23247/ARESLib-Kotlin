package com.areslib.ftc.telemetry

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Configuration schema for a single Limelight camera.
 * @param name The descriptive name of the camera (e.g., "Front", "Back").
 * @param targetIp The static USB-C IP address assigned to this camera (e.g., "10.252.252.242").
 * @param localPortOffset The port offset from 5800 (e.g., 0 maps to 5800/5801/5802; 10 maps to 5810/5811/5812).
 */
data class LimelightConfig(
    val name: String,
    val targetIp: String,
    val localPortOffset: Int = 0
)

/**
 * A highly optimized, modular, and dynamic TCP Port Forwarder and Network Proxy.
 * Runs at the raw socket level in background threads, enabling wireless access 
 * to multiple Limelight web configuration dashboards and camera streams 
 * over the Control Hub's Wi-Fi connection.
 */
class LimelightProxy(
    private val cameras: List<LimelightConfig> = listOf(
        LimelightConfig("Front", "172.29.0.1", 0) // Default single-camera setup
    )
) {
    private val executor = Executors.newCachedThreadPool()
    private val forwarders = mutableListOf<TCPForwarder>()

    /**
     * Starts the wireless network tunnels for all configured cameras.
     */
    fun start() {
        for (camera in cameras) {
            val basePort = 5800 + camera.localPortOffset
            
            // 1. Forward the Web UI Dashboard (HTTP/REST)
            startForwarder(basePort + 1, camera.targetIp, 5801)

            // 2. Forward all other Limelight streaming, websocket, and status API ports (5800 to 5807)
            startForwarder(basePort, camera.targetIp, 5800)
            startForwarder(basePort + 2, camera.targetIp, 5802)
            startForwarder(basePort + 3, camera.targetIp, 5803)
            startForwarder(basePort + 4, camera.targetIp, 5804)
            startForwarder(basePort + 5, camera.targetIp, 5805)
            startForwarder(basePort + 6, camera.targetIp, 5806)
            startForwarder(basePort + 7, camera.targetIp, 5807)

            System.out.println(
                "LimelightProxy: Started '${camera.name}' camera tunnel. " +
                "Dashboard: http://192.168.43.1:${basePort + 1} | Stream: http://192.168.43.1:${basePort}"
            )
        }
        System.out.println("LimelightProxy: All active wireless tunnels successfully initialized!")
    }

    private fun startForwarder(localPort: Int, remoteHost: String, remotePort: Int) {
        val forwarder = TCPForwarder(localPort, remoteHost, remotePort)
        forwarders.add(forwarder)
        executor.submit(forwarder)
    }

    /**
     * Shuts down all active network tunnels and releases system sockets cleanly.
     */
    fun stop() {
        forwarders.forEach { it.stop() }
        forwarders.clear()
        executor.shutdownNow()
        System.out.println("LimelightProxy: All wireless tunnels stopped cleanly.")
    }

    private inner class TCPForwarder(
        private val localPort: Int,
        private val remoteHost: String,
        private val remotePort: Int
    ) : Runnable {
        private var serverSocket: ServerSocket? = null
        @Volatile private var running = true

        override fun run() {
            try {
                serverSocket = ServerSocket(localPort)
                while (running) {
                    val clientSocket = serverSocket?.accept() ?: break
                    // Dispatch each browser request to the cached thread pool
                    executor.submit { handleConnection(clientSocket) }
                }
            } catch (e: IOException) {
                // Server socket closed
            }
        }

        private fun handleConnection(clientSocket: Socket) {
            var remoteSocket: Socket? = null
            try {
                // Set read timeouts to prevent HTTP keep-alive sockets from leaking threads forever.
                // Long-lived persistent connections (Stream, WebSockets, status APIs) must not time out.
                val isPersistent = (remotePort != 5801)
                val timeoutMs = if (isPersistent) 0 else 15000

                clientSocket.soTimeout = timeoutMs

                remoteSocket = Socket(remoteHost, remotePort)
                remoteSocket.soTimeout = timeoutMs

                val clientIn = clientSocket.getInputStream()
                val clientOut = clientSocket.getOutputStream()
                val remoteIn = remoteSocket.getInputStream()
                val remoteOut = remoteSocket.getOutputStream()

                // Bidirectional copy using the cached thread pool
                val f1 = executor.submit {
                    try {
                        copyStream(clientIn, remoteOut)
                    } finally {
                        // Close remote socket to unblock the other thread
                        try { remoteSocket.close() } catch (ignored: Exception) {}
                    }
                }

                val f2 = executor.submit {
                    try {
                        copyStream(remoteIn, clientOut)
                    } finally {
                        // Close client socket to unblock the other thread
                        try { clientSocket.close() } catch (ignored: Exception) {}
                    }
                }

                // Wait for both copy directions to finish
                f1.get()
                f2.get()

            } catch (e: Exception) {
                // Handle socket close/timeout silently
            } finally {
                try { clientSocket.close() } catch (ignored: Exception) {}
                try { remoteSocket?.close() } catch (ignored: Exception) {}
            }
        }

        private fun copyStream(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(8192)
            var bytesRead = 0
            try {
                while (running && input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                }
            } catch (e: IOException) {
                // Link disconnected
            }
        }

        fun stop() {
            running = false
            try {
                serverSocket?.close()
            } catch (ignored: IOException) {}
        }
    }
}
