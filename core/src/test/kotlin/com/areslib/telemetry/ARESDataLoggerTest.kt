package com.areslib.telemetry

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ARESDataLoggerTest {

    @Test
    fun testAsyncCSVLogging() {
        val telemetry = DataLoggingTelemetry()
        
        // Log 3 mock frames
        for (i in 1..3) {
            telemetry.putNumber("Test/Value", i.toDouble())
            telemetry.putBoolean("Test/State", i % 2 == 0)
            telemetry.update()
            Thread.sleep(50) // Simulate loop time
        }

        // Close to flush all frames and wait for file IO completion
        telemetry.close()

        // Verify logs directory and contents
        val logsDir = File("./logs/")
        assertTrue(logsDir.exists(), "Logs directory should be created")

        val logFiles = logsDir.listFiles { _, name -> name.startsWith("ares_log_") && name.endsWith(".csv") }
        assertTrue(logFiles != null && logFiles.isNotEmpty(), "At least one log file should be generated")

        // Read the latest log file
        val latestLog = logFiles.maxByOrNull { it.lastModified() }!!
        val lines = latestLog.readLines()

        assertTrue(lines.size >= 4, "Log should contain header + at least 3 data rows")
        
        // Confirm headers contain our fields
        val header = lines[0]
        assertTrue(header.contains("TimestampMs"), "Header must include timestamp")
        assertTrue(header.contains("Test/Value"), "Header must include Test/Value")
        assertTrue(header.contains("Test/State"), "Header must include Test/State")

        // Cleanup the test log file so we don't litter
        latestLog.delete()
    }
}
