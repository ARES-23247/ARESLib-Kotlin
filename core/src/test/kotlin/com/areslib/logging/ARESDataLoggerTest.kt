package com.areslib.logging

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlin.test.assertEquals

/**
 * ARESDataLoggerTest declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class ARESDataLoggerTest {

    @Test
    /**
     * testAsyncCSVLogging declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
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

    @Test
    /**
     * testMapPoolingAndZeroAllocations declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun testMapPoolingAndZeroAllocations() {
        val logger = ARESDataLogger()
        
        // 1. Exhaust the pre-populated pool of 16 maps to test behavior when empty
        val exhaustedMaps = mutableListOf<HashMap<String, Any>>()
        for (i in 0 until 16) {
            exhaustedMaps.add(logger.obtainMap())
        }

        // 2. Obtain a map when the pool is empty - this allocates a new map
        val map1 = logger.obtainMap()
        assertTrue(map1.isEmpty(), "Obtained map should be clean and empty")
        
        // 3. Put some dummy data and recycle it
        map1["Key1"] = 1.0
        logger.recycleMap(map1)
        assertTrue(map1.isEmpty(), "Recycled map must be cleared upon recycling")

        // 4. Obtain a map again - should return the exact same instance because it is the only one in the pool now
        val map2 = logger.obtainMap()
        assertSame(map1, map2, "The pool must return the recycled map instance to achieve zero allocations")

        // 5. Recycle everything to cleanup and ensure proper behavior
        exhaustedMaps.forEach { logger.recycleMap(it) }
        logger.recycleMap(map2)
        
        logger.stop()
    }

    @Test
    /**
     * testDataLoggingThrottle declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun testDataLoggingThrottle() {
        com.areslib.util.RobotClock.useMockTime(1000L)
        try {
            val telemetry = DataLoggingTelemetry()
            telemetry.minLogIntervalMs = 50L // 50ms interval

            // Log 5 times in rapid succession (dt = 0ms)
            for (i in 1..5) {
                telemetry.putNumber("Test/Throttle", i.toDouble())
                telemetry.update()
            }

            // Move clock forward by 60ms to exceed the interval and log once more
            com.areslib.util.RobotClock.useMockTime(1060L)
            telemetry.putNumber("Test/Throttle", 100.0)
            telemetry.update()

            // Close to flush
            telemetry.close()

            val logsDir = File("./logs/")
            assertTrue(logsDir.exists(), "Logs directory should exist")

            val logFiles = logsDir.listFiles { _, name -> name.startsWith("ares_log_") && name.endsWith(".csv") }
            assertTrue(logFiles != null && logFiles.isNotEmpty())

            val latestLog = logFiles.maxByOrNull { it.lastModified() }!!
            val lines = latestLog.readLines()

            // We expect:
            // Line 0: Header
            // Line 1: First frame (written at t = 1000ms)
            // Line 2: Second frame (written after mock time elapsed by 60ms)
            // Total lines should be exactly 3
            assertEquals(3, lines.size, "Logging throttle should restrict output to exactly 2 frames")

            // Cleanup
            latestLog.delete()
        } finally {
            com.areslib.util.RobotClock.useSystemTime()
        }
    }
}
