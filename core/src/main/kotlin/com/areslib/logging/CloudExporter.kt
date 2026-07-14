package com.areslib.logging

import com.areslib.telemetry.RobotStatusTracker
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object CloudExporter {
    val isAndroid: Boolean by lazy {
        val javaVendor = System.getProperty("java.vendor") ?: ""
        javaVendor.contains("Android", ignoreCase = true) || File("/sdcard").exists()
    }

    @Volatile
    var areswebServerUrl: String = ""

    init {
        areswebServerUrl = "https://ares-analytics-gateway-staging-205869391101.us-central1.run.app/api"
        System.getenv("ARESWEB_API_URL")?.let { areswebServerUrl = it }
    }

    val logDir: File by lazy {
        if (isAndroid) File("/sdcard/FIRST/telemetry_logs/") else File("./logs/")
    }

    /**
     * Manually upload a single log file to the cloud.
     * @return true if successful, false otherwise.
     */
    fun uploadFile(file: File): String? {
        if (!file.exists()) return "File not found"

        // If file is older than 4 hours, auto-archive instead of uploading to save cloud costs
        if (System.currentTimeMillis() - file.lastModified() > 4 * 60 * 60 * 1000) {
            println("CloudExporter: File ${file.name} is too old. Auto-archiving without upload.")
            archiveFile(file)
            return null // Considered success
        }

        val route = when {
            file.name.startsWith("ares_log_") -> "/upload/telemetry"
            file.name.startsWith("action_log_") -> "/upload/actions"
            else -> "/upload/generic"
        }
        val targetUrl = "$areswebServerUrl$route"
        val contentType = if (file.name.endsWith(".jsonl")) "application/x-jsonlines" else "text/csv"
        
        RobotStatusTracker.activeUploadFile = file.name
        RobotStatusTracker.uploadProgress = 0.0

        println("CloudExporter: Starting manual upload of ${file.name} to $targetUrl...")
        val error = uploadFileWithRetry(file, targetUrl, contentType)
        
        if (error == null) {
            println("CloudExporter: Successfully uploaded ${file.name}. Archiving locally...")
            archiveFile(file)
        } else {
            System.err.println("CloudExporter: Failed to upload ${file.name}: $error")
        }
        
        RobotStatusTracker.activeUploadFile = null
        RobotStatusTracker.uploadProgress = 0.0
        return error
    }

    private fun isServerReachable(): Boolean {
        return try {
            val url = URL(areswebServerUrl.substringBefore("/api") + "/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
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

    private fun uploadFileWithRetry(file: File, targetUrl: String, contentType: String): String? {
        var backoffMs = 1000L
        var lastError: String? = null
        for (attempt in 1..3) {
            val error = performUpload(file, targetUrl, contentType)
            if (error == null) {
                return null
            }
            lastError = error
            if (attempt < 3) {
                try {
                    Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return "Interrupted: $lastError"
                }
                backoffMs *= 2 // Exponential backoff (1s -> 2s -> 4s)
            }
        }
        return lastError
    }

    private fun performUpload(file: File, targetUrl: String, contentType: String): String? {
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
            val uploadFileName = if (isAndroid) file.name else "sim_${file.name}"
            conn.setRequestProperty("X-FileName", uploadFileName)
            
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
            return if (responseCode in 200..299) {
                null
            } else {
                "HTTP $responseCode from $targetUrl"
            }
        } catch (e: Exception) {
            System.err.println("CloudExporter: upload error: ${e.message}")
            return "Exception: ${e.message}"
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
