package com.areslib.telemetry

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object CloudExporter {
    @Volatile
    var areswebServerUrl: String = "http://localhost:5001/aresweb-23247/us-central1/api"

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { thread ->
        Thread(thread, "ARES-CloudExporter-Thread").apply { isDaemon = true }
    }

    private val logDir: File by lazy {
        val javaVendor = System.getProperty("java.vendor") ?: ""
        val isAndroid = javaVendor.contains("Android", ignoreCase = true) || File("/sdcard").exists()
        if (isAndroid) File("/sdcard/FIRST/telemetry_logs/") else File("./logs/")
    }

    @Volatile
    private var isUploading = false

    /**
     * Start background log checker and exporter task.
     */
    fun start() {
        executor.scheduleWithFixedDelay({
            try {
                exportLogs()
            } catch (e: Exception) {
                System.err.println("CloudExporter: Error in background export task: ${e.message}")
            }
        }, 5, 10, TimeUnit.SECONDS)
    }

    private fun exportLogs() {
        if (isUploading) return
        if (!logDir.exists()) return

        val files = logDir.listFiles { _, name ->
            (name.startsWith("state_log_") && name.endsWith(".jsonl")) ||
            (name.startsWith("action_log_") && name.endsWith(".jsonl")) ||
            (name.startsWith("input_log_") && name.endsWith(".jsonl")) ||
            (name.startsWith("motor_log_") && name.endsWith(".csv")) ||
            (name.startsWith("vision_log_") && name.endsWith(".jsonl"))
        } ?: return

        val now = System.currentTimeMillis()
        val uploadableFiles = files.filter { file ->
            // Skip files modified in the last 5 seconds to avoid uploading active logs
            now - file.lastModified() > 5000L
        }.sortedBy { it.lastModified() }

        if (uploadableFiles.isEmpty()) return

        // Check if server is reachable before attempting uploads
        if (!isServerReachable()) return

        isUploading = true
        try {
            for (file in uploadableFiles) {
                val route = when {
                    file.name.startsWith("state_log_") -> "/api/upload/states"
                    file.name.startsWith("action_log_") -> "/api/upload/actions"
                    file.name.startsWith("input_log_") -> "/api/upload/inputs"
                    file.name.startsWith("motor_log_") -> "/api/upload/motors"
                    file.name.startsWith("vision_log_") -> "/api/upload/vision"
                    else -> continue
                }
                
                val contentType = if (file.name.endsWith(".jsonl")) "application/x-jsonlines" else "text/csv"
                
                RobotStatusTracker.activeUploadFile = file.name
                RobotStatusTracker.uploadProgress = 0.0

                println("CloudExporter: Starting upload of ${file.name} to $route...")
                val success = uploadFileWithRetry(file, "$areswebServerUrl$route", contentType)
                
                if (success) {
                    println("CloudExporter: Successfully uploaded ${file.name}. Archiving locally...")
                    archiveFile(file)
                } else {
                    System.err.println("CloudExporter: Failed to upload ${file.name}. Will retry later.")
                    break // Stop uploading further files if one fails
                }
            }
        } finally {
            RobotStatusTracker.activeUploadFile = null
            RobotStatusTracker.uploadProgress = 0.0
            isUploading = false
        }
    }

    private fun isServerReachable(): Boolean {
        return try {
            val url = URL("$areswebServerUrl/status") // Assuming status or simple endpoint exists
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            // Check fallback base url connectivity
            try {
                val url = URL(areswebServerUrl.substringBefore("/api"))
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                code in 200..399
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun uploadFileWithRetry(file: File, targetUrl: String, contentType: String): Boolean {
        var backoffMs = 1000L
        for (attempt in 1..3) {
            if (performUpload(file, targetUrl, contentType)) {
                return true
            }
            if (attempt < 3) {
                try {
                    Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
                backoffMs *= 2 // Exponential backoff (1s -> 2s -> 4s)
            }
        }
        return false
    }

    private fun performUpload(file: File, targetUrl: String, contentType: String): Boolean {
        var conn: HttpURLConnection? = null
        var fis: FileInputStream? = null
        try {
            val url = URL(targetUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", contentType)
            conn.setRequestProperty("X-FileName", file.name)
            
            // Stream in 1MB chunks to avoid loading whole file into memory (OOM safety)
            val chunkSize = 1024 * 1024
            conn.setChunkedStreamingMode(chunkSize)

            fis = FileInputStream(file)
            val os: OutputStream = conn.outputStream
            val buffer = ByteArray(4096)
            val totalBytes = file.length()
            var bytesWritten = 0L

            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                os.write(buffer, 0, bytesRead)
                bytesWritten += bytesRead
                if (totalBytes > 0) {
                    RobotStatusTracker.uploadProgress = (bytesWritten.toDouble() / totalBytes * 100.0)
                }
            }
            os.flush()
            os.close()

            val responseCode = conn.responseCode
            return responseCode in 200..299
        } catch (e: Exception) {
            System.err.println("CloudExporter: upload error: ${e.message}")
            return false
        } finally {
            try { fis?.close() } catch (_: Exception) {}
            conn?.disconnect()
        }
    }

    private fun archiveFile(file: File) {
        try {
            val syncedDir = File(logDir, "synced")
            if (!syncedDir.exists()) {
                syncedDir.mkdirs()
            }
            val destFile = File(syncedDir, file.name)
            if (destFile.exists()) {
                destFile.delete() // Overwrite if duplicate exists
            }
            val success = file.renameTo(destFile)
            if (!success) {
                // Failsafe copy & delete if rename fails
                file.copyTo(destFile, overwrite = true)
                file.delete()
            }
        } catch (e: Exception) {
            System.err.println("CloudExporter: Failed to archive file ${file.name}: ${e.message}")
        }
    }
}
