package com.areslib.telemetry.web

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Object implementation for Log Archive Packager.
 *
 * Real-time telemetry streaming, diagnostic logging, and NetworkTables 4 communication handler.
 */
object LogArchivePackager {
    /**
     * listLogFiles declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun listLogFiles(logDir: File): List<String> {
        if (!logDir.exists()) return emptyList()
        val files = logDir.listFiles { _, name -> name.endsWith(".csv") || name.endsWith(".jsonl") } ?: emptyArray()
        return files.sortedBy { it.name }.map { it.name }
    }

    /**
     * isValidLogFile declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun isValidLogFile(logDir: File, fileName: String): Boolean {
        val file = File(logDir, fileName)
        return file.exists() && (file.name.endsWith(".csv") || file.name.endsWith(".jsonl"))
    }

    /**
     * streamLogFile declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun streamLogFile(logDir: File, fileName: String, out: OutputStream) {
        val file = File(logDir, fileName)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
        }
    }

    /**
     * getFileLength declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getFileLength(logDir: File, fileName: String): Long {
        return File(logDir, fileName).length()
    }

    /**
     * markSynced declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun markSynced(logDir: File, fileName: String): Boolean {
        val file = File(logDir, fileName)
        val syncedDir = File(logDir, "synced")
        if (!syncedDir.exists()) {
            syncedDir.mkdirs()
        }
        val destFile = File(syncedDir, file.name)
        return file.renameTo(destFile)
    }
}
