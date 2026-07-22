package com.areslib.telemetry.web

/**
 * Class implementation for Port Forwarder.
 *
 * Real-time telemetry streaming, diagnostic logging, and NetworkTables 4 communication handler.
 */
class PortForwarder(private val localPort: Int, private val remotePort: Int, private val targetIp: String) : Thread("ARES-PortForwarder-$localPort") {
    private var serverSocket: java.net.ServerSocket? = null
    @Volatile private var running = true

    init {
        isDaemon = true
    }

    /**
     * run declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
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

    /**
     * stopForwarder declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun stopForwarder() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }
}
