package com.areslib.logging

import com.google.gson.Gson
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Thread-safe, high-performance asynchronous JSONL recorder for RobotInputsFrames.
 * Logs sensory data frames on a separate thread and automatically recycles them
 * back to the RobotInputsFramePool to achieve zero runtime GC allocations in the control loop.
 */
class InputLogger(customLogFile: File? = null) {
    private val gson = Gson()
    private val queue = LinkedBlockingQueue<RobotInputsFrame>(1000)
    private var writer: BufferedWriter? = null
    private var isRunning = false
    
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { thread -> Thread(thread, "ARES-InputLogger-Thread").apply { isDaemon = true } }
    )

    init {
        try {
            if (customLogFile != null) {
                val parentDir = customLogFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }
                writer = BufferedWriter(FileWriter(customLogFile))
                isRunning = true
                startLoggingLoop()
            } else {
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
                val logFile = File(logDir, "input_log_$timestamp.jsonl")

                writer = BufferedWriter(FileWriter(logFile))
                isRunning = true
                startLoggingLoop()
            }
        } catch (e: Exception) {
            System.err.println("InputLogger: Failed to initialize! ${e.message}")
            isRunning = false
        }
    }

    /**
     * Submit a populated frame to the asynchronous logger queue.
     * WARNING: Once offered, the frame belongs to the logger thread and is recycled.
     * The main loop thread must NOT touch or modify this frame object after calling this method.
     */
    fun logFrame(frame: RobotInputsFrame) {
        if (!isRunning) {
            // Failsafe: recycle immediately if logger is inactive
            RobotInputsFramePool.recycle(frame)
            return
        }
        val accepted = queue.offer(frame)
        if (!accepted) {
            RobotInputsFramePool.recycle(frame)
        }
    }

    private fun startLoggingLoop() {
        executor.submit {
            while (isRunning || queue.isNotEmpty()) {
                try {
                    val frame = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    writeFrame(frame)
                    // Automatically recycle the frame to the pool for reuse!
                    RobotInputsFramePool.recycle(frame)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    System.err.println("InputLogger: Error logging frame: ${e.message}")
                }
            }
            closeWriter()
        }
    }

    private fun writeFrame(frame: RobotInputsFrame) {
        val w = writer ?: return
        try {
            w.write(gson.toJson(frame))
            w.newLine()
            w.flush()
        } catch (e: IOException) {
            System.err.println("InputLogger: Failed to write JSONL: ${e.message}")
        }
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (e: IOException) {
            System.err.println("InputLogger: Failed to close writer: ${e.message}")
        }
    }

    /**
     * Terminate the logging worker thread, flushing any remaining queued frames to disk.
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
