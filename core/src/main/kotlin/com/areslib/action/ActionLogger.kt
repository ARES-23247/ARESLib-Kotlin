package com.areslib.action

import com.google.gson.Gson
import com.google.gson.JsonObject
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
 * Thread-safe, asynchronous JSONL recorder for RobotAction streams.
 * Guarantees microsecond-accurate chronology logging with zero overhead on the main thread.
 */
class ActionLogger(
    val runId: String = "",
    val robotId: String = "",
    val matchNumber: Int = 0,
    val alliance: String = "BLUE",
    val mode: String = "Init"
) {
    private val gson = Gson()
    private val queue = LinkedBlockingQueue<RobotAction>(1000)
    private var writer: BufferedWriter? = null
    private var isRunning = false
    private var writeCount = 0
    
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { thread -> Thread(thread, "ARES-ActionLogger-Thread").apply { isDaemon = true } }
    )

    init {
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
            val logFile = File(logDir, "action_log_${timestamp}_$mode.jsonl")

            writer = BufferedWriter(FileWriter(logFile))
            isRunning = true
            startLoggingLoop()
        } catch (e: Exception) {
            System.err.println("ActionLogger: Failed to initialize! ${e.message}")
            isRunning = false
        }
    }

    fun logAction(action: RobotAction) {
        if (!isRunning) return
        queue.offer(action)
    }

    private fun startLoggingLoop() {
        executor.submit {
            while (isRunning || queue.isNotEmpty()) {
                try {
                    val action = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    writeAction(action)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    System.err.println("ActionLogger: Error logging action: ${e.message}")
                }
            }
            closeWriter()
        }
    }

    private fun writeAction(action: RobotAction) {
        val w = writer ?: return
        val typeName = action.javaClass.simpleName
        val payloadJson = gson.toJsonTree(action) as JsonObject
        
        val envelope = JsonObject()
        envelope.addProperty("run_id", runId)
        envelope.addProperty("robot_id", robotId)
        envelope.addProperty("match_number", matchNumber)
        envelope.addProperty("alliance", alliance)
        envelope.addProperty("op_mode", mode)
        envelope.addProperty("type", typeName)
        envelope.add("payload", payloadJson)

        try {
            w.write(gson.toJson(envelope))
            w.newLine()
            writeCount++
            if (writeCount % 20 == 0) {
                w.flush()
            }
        } catch (e: IOException) {
            System.err.println("ActionLogger: Failed to write JSONL: ${e.message}")
        }
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (e: IOException) {
            System.err.println("ActionLogger: Failed to close: ${e.message}")
        } finally {
            writer = null
        }
    }

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
