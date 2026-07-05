package com.areslib.ftc.telemetry

import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.Executors

/**
 * Asynchronous, thread-safe uploader that pushes compiled CSV logs directly
 * to a dedicated stream and topic on your Zulip Cloud organization.
 * Built using pure Java standard HTTP libraries to guarantee zero compile dependencies.
 */
class ZulipLogUploader private constructor(
    private val orgUrl: String, // e.g., "https://yourteam.zulipchat.com"
    private val botEmail: String,
    private val botApiKey: String,
    private val targetStream: String,
    private val targetTopic: String,
    private val isConfigured: Boolean
) {
    /**
     * Secondary constructor for backwards compatibility or manual credential injection.
     */
    constructor(
        orgUrl: String,
        botEmail: String,
        botApiKey: String,
        targetStream: String = "robot-telemetry",
        targetTopic: String = "Practice Logs"
    ) : this(
        orgUrl,
        botEmail,
        botApiKey,
        targetStream,
        targetTopic,
        orgUrl.isNotEmpty() && botEmail.isNotEmpty() && botApiKey.isNotEmpty()
    )

    private val executor = Executors.newSingleThreadExecutor()

    private val authHeader: String by lazy {
        val credentials = "$botEmail:$botApiKey"
        val encoded = android.util.Base64.encodeToString(
            credentials.toByteArray(StandardCharsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        "Basic $encoded"
    }

    /**
     * Checks the telemetry logs directory and uploads any new files asynchronously.
     */
    fun checkAndUpload() {
        if (!isConfigured) {
            System.out.println("ZulipLogUploader: Uploader is not configured. Skipping telemetry upload.")
            return
        }
        executor.submit {
            try {
                val logDir = File("/sdcard/FIRST/telemetry_logs/")
                if (!logDir.exists() || !logDir.isDirectory) return@submit

                val files = logDir.listFiles { _, name -> name.endsWith(".csv") } ?: return@submit

                for (file in files) {
                    val uploadedMark = File(file.absolutePath + ".uploaded")
                    if (uploadedMark.exists()) continue // Skip already uploaded

                    uploadFileToZulip(file, uploadedMark)
                }
            } catch (e: Exception) {
                System.err.println("ZulipLogUploader: Error during directory scanning: ${e.message}")
            }
        }
    }

    private fun uploadFileToZulip(file: File, markFile: File) {
        var conn: HttpURLConnection? = null
        try {
            val boundary = "===Boundary-${System.currentTimeMillis()}==="
            val url = URL("$orgUrl/api/v1/user_uploads")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val out = conn.outputStream
            val writer = PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8), true)

            // Write multipart file header
            writer.append("--$boundary").append("\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"").append("\r\n")
            writer.append("Content-Type: text/csv").append("\r\n")
            writer.append("\r\n").flush()

            // Stream file bytes to socket
            val buffer = ByteArray(4096)
            var bytesRead: Int
            val fileIn = FileInputStream(file)
            while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
            out.flush()
            fileIn.close()

            // Close multipart body
            writer.append("\r\n").flush()
            writer.append("--$boundary--").append("\r\n").flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseReader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val response = StringBuilder()
                var line: String?
                while (responseReader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                responseReader.close()

                val json = JSONObject(response.toString())
                val fileUri = json.optString("uri", "")
                if (fileUri.isNotEmpty()) {
                    postLinkToStream(file.name, fileUri, markFile)
                }
            } else {
                System.err.println("ZulipLogUploader: Upload failed for ${file.name} with code $responseCode")
            }
        } catch (e: Exception) {
            System.err.println("ZulipLogUploader: Network error during upload of ${file.name}: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    private fun postLinkToStream(fileName: String, fileUri: String, markFile: File) {
        var conn: HttpURLConnection? = null
        try {
            val markdownContent = "📊 **New Telemetry Log Uploaded:** [$fileName]($fileUri)"
            val url = URL("$orgUrl/api/v1/messages")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            // Encode post data
            val postData = StringBuilder()
            postData.append("type=").append(java.net.URLEncoder.encode("stream", "UTF-8"))
            postData.append("&to=").append(java.net.URLEncoder.encode(targetStream, "UTF-8"))
            postData.append("&topic=").append(java.net.URLEncoder.encode(targetTopic, "UTF-8"))
            postData.append("&content=").append(java.net.URLEncoder.encode(markdownContent, "UTF-8"))

            val out = conn.outputStream
            out.write(postData.toString().toByteArray(StandardCharsets.UTF_8))
            out.flush()
            out.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try {
                    markFile.createNewFile()
                    println("ZulipLogUploader: Log $fileName successfully shared to stream #$targetStream!")
                } catch (e: IOException) {
                    System.err.println("ZulipLogUploader: Failed to create upload mark for $fileName: ${e.message}")
                }
            } else {
                System.err.println("ZulipLogUploader: Message post failed with code $responseCode")
            }
        } catch (e: Exception) {
            System.err.println("ZulipLogUploader: Network error during stream post: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val DEFAULT_PROPERTIES_PATH = "/sdcard/FIRST/zulip.properties"

        /**
         * Automatically loads credentials from /sdcard/FIRST/zulip.properties on the Control Hub.
         * If the file is not found, it returns an unconfigured uploader that fails gracefully.
         */
        @JvmStatic
        fun createAutoConfigured(): ZulipLogUploader {
            val file = File(DEFAULT_PROPERTIES_PATH)
            if (!file.exists()) {
                System.out.println("ZulipLogUploader: Properties file not found at $DEFAULT_PROPERTIES_PATH. Telemetry upload disabled.")
                return ZulipLogUploader("", "", "", "", "", false)
            }
            return try {
                val props = Properties()
                FileInputStream(file).use { props.load(it) }
                val orgUrl = props.getProperty("orgUrl", "").trim()
                val botEmail = props.getProperty("botEmail", "").trim()
                val botApiKey = props.getProperty("botApiKey", "").trim()
                val targetStream = props.getProperty("targetStream", "robot-telemetry").trim()
                val targetTopic = props.getProperty("targetTopic", "Practice Logs").trim()

                if (orgUrl.isEmpty() || botEmail.isEmpty() || botApiKey.isEmpty()) {
                    System.err.println("ZulipLogUploader: Properties file at $DEFAULT_PROPERTIES_PATH is missing required keys (orgUrl, botEmail, botApiKey).")
                    ZulipLogUploader("", "", "", "", "", false)
                } else {
                    ZulipLogUploader(orgUrl, botEmail, botApiKey, targetStream, targetTopic, true)
                }
            } catch (e: Exception) {
                System.err.println("ZulipLogUploader: Failed to parse properties file at $DEFAULT_PROPERTIES_PATH: ${e.message}")
                ZulipLogUploader("", "", "", "", "", false)
            }
        }
    }
}
