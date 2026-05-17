package com.areslib.telemetry

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Asynchronous, thread-safe file-based CSV data logger.
 * Offloads all file writing operations to a background thread to guarantee
 * zero impact on the robot controller's primary control loop.
 */
class ARESDataLogger {

    private val logQueue = LinkedBlockingQueue<Map<String, Any>>()
    private val activeKeys = mutableListOf<String>()
    private var writer: BufferedWriter? = null
    private var isHeaderWritten = false
    private var isRunning = false
    
    // Executor that processes the queue sequentially
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue()
    )

    init {
        try {
            val osName = System.getProperty("os.name") ?: ""
            val javaVendor = System.getProperty("java.vendor") ?: ""
            val isAndroid = javaVendor.contains("Android", ignoreCase = true) || File("/sdcard").exists()

            val logDir = if (isAndroid) {
                File("/sdcard/FIRST/telemetry_logs/")
            } else {
                File("./logs/")
            }

            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "ares_log_$timestamp.csv")

            writer = BufferedWriter(FileWriter(logFile))
            isRunning = true
            startLoggingLoop()
        } catch (e: Exception) {
            System.err.println("ARESDataLogger: Failed to initialize log file! ${e.message}")
            isRunning = false
        }
    }

    /**
     * Queues a frame of key-value telemetry data to be logged.
     */
    fun logFrame(data: Map<String, Any>) {
        if (!isRunning) return
        logQueue.offer(data)
    }

    private fun startLoggingLoop() {
        executor.submit {
            while (isRunning || logQueue.isNotEmpty()) {
                try {
                    val frame = logQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    writeFrame(frame)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    System.err.println("ARESDataLogger: Error writing log frame: ${e.message}")
                }
            }
            closeWriter()
        }
    }

    private fun writeFrame(frame: Map<String, Any>) {
        val w = writer ?: return

        // 1. Write the CSV header on the first frame
        if (!isHeaderWritten) {
            activeKeys.clear()
            // Always place timestamp first for easier plotting
            activeKeys.add("TimestampMs")
            
            // Add all other keys alphabetically
            frame.keys.sorted().forEach { key ->
                if (key != "TimestampMs") {
                    activeKeys.add(key)
                }
            }

            try {
                w.write(activeKeys.joinToString(","))
                w.newLine()
                isHeaderWritten = true
            } catch (e: IOException) {
                System.err.println("ARESDataLogger: Failed to write CSV header: ${e.message}")
            }
        }

        // 2. Write values corresponding to the configured headers
        val row = activeKeys.map { key ->
            frame[key]?.toString() ?: ""
        }

        try {
            w.write(row.joinToString(","))
            w.newLine()
            w.flush()
        } catch (e: IOException) {
            System.err.println("ARESDataLogger: Failed to write CSV row: ${e.message}")
        }
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (e: IOException) {
            System.err.println("ARESDataLogger: Failed to close writer: ${e.message}")
        } finally {
            writer = null
        }
    }

    /**
     * Gracefully stops the background logging worker after flushing all remaining queued items.
     */
    fun stop() {
        isRunning = false
        executor.shutdown()
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
