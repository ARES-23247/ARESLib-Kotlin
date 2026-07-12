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
class ARESDataLogger(val mode: String = "Init") {

    private val logQueue = LinkedBlockingQueue<Map<String, Any>>(1000)
    private val activeKeys = mutableListOf<String>()
    private var writer: BufferedWriter? = null
    private var isHeaderWritten = false
    private var isRunning = false

    // GC-Free Map Pool to eliminate allocations during telemetry updates
    private val mapPool = LinkedBlockingQueue<HashMap<String, Any>>()

    // Executor that processes the queue sequentially using daemon threads to prevent JVM hanging
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { thread -> Thread(thread, "ARES-DataLogger-Thread").apply { isDaemon = true } }
    )

    init {
        // Pre-populate the map pool with 16 instances
        for (i in 0 until 16) {
            mapPool.offer(HashMap())
        }

        try {
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

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "ares_log_${timestamp}_$mode.csv")

            writer = BufferedWriter(FileWriter(logFile))
            isRunning = true
            startLoggingLoop()
        } catch (e: Exception) {
            System.err.println("ARESDataLogger: Failed to initialize log file! ${e.message}")
            isRunning = false
        }
    }

    /**
     * Obtains a cleared HashMap from the reusable pool, or instantiates a new one if pool is depleted.
     */
    fun obtainMap(): HashMap<String, Any> {
        return mapPool.poll() ?: HashMap()
    }

    /**
     * Clears and returns a HashMap to the pool for future reuse.
     */
    fun recycleMap(map: HashMap<String, Any>) {
        map.clear()
        mapPool.offer(map)
    }

    /**
     * Queues a frame of key-value telemetry data to be logged.
     */
    fun logFrame(data: Map<String, Any>) {
        if (!isRunning) {
            if (data is HashMap<String, Any>) {
                recycleMap(data)
            }
            return
        }
        val accepted = logQueue.offer(data)
        if (!accepted && data is HashMap<String, Any>) {
            recycleMap(data)
        }
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

    // Pre-allocated StringBuilder for zero-allocation CSV row writing
    private val csvBuilder = StringBuilder(512)

    private fun writeFrame(frame: Map<String, Any>) {
        val w = writer ?: return

        try {
            // 1. Write the CSV header on the first frame
            if (!isHeaderWritten) {
                activeKeys.clear()
                // Always place timestamp first for easier plotting
                activeKeys.add("TimestampMs")
                
                // Add all other keys alphabetically
                val sortedKeys = frame.keys.sorted()
                for (i in 0 until sortedKeys.size) {
                    val key = sortedKeys[i]
                    if (key != "TimestampMs") {
                        activeKeys.add(key)
                    }
                }

                try {
                    csvBuilder.setLength(0)
                    for (i in 0 until activeKeys.size) {
                        if (i > 0) csvBuilder.append(',')
                        csvBuilder.append(activeKeys[i])
                    }
                    w.write(csvBuilder.toString())
                    w.newLine()
                    isHeaderWritten = true
                } catch (e: IOException) {
                    System.err.println("ARESDataLogger: Failed to write CSV header: ${e.message}")
                }
            }

            // 2. Write values corresponding to the configured headers
            try {
                csvBuilder.setLength(0)
                for (i in 0 until activeKeys.size) {
                    if (i > 0) csvBuilder.append(',')
                    val value = frame[activeKeys[i]]
                    if (value != null) csvBuilder.append(value.toString())
                }
                w.write(csvBuilder.toString())
                w.newLine()
                w.flush()
            } catch (e: IOException) {
                System.err.println("ARESDataLogger: Failed to write CSV row: ${e.message}")
            }
        } finally {
            // Recycle the map if it was obtained from the map pool to eliminate GC pressure
            if (frame is HashMap<String, Any>) {
                recycleMap(frame)
            }
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
