package com.areslib.logging

import com.areslib.util.RobotClock
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputLoggerTest {
    private lateinit var tempFile: File
    private val gson = Gson()

    @BeforeTest
    fun setUp() {
        tempFile = File.createTempFile("input_logger_test_", ".jsonl")
        RobotClock.useSystemTime()
    }

    @AfterTest
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
        RobotClock.useSystemTime()
    }

    @Test
    fun testRobotClockMocking() {
        // By default, clock is system clock
        assertFalse(RobotClock.isMocked)
        val t1 = RobotClock.currentTimeMillis()
        assertTrue(t1 > 0L)

        // Mock the clock
        val mockTime = 123456789000L
        RobotClock.useMockTime(mockTime)
        assertTrue(RobotClock.isMocked)
        assertEquals(mockTime, RobotClock.currentTimeMillis())

        // Revert clock
        RobotClock.useSystemTime()
        assertFalse(RobotClock.isMocked)
        val t2 = RobotClock.currentTimeMillis()
        assertTrue(t2 >= t1)
    }

    @Test
    fun testFramePoolReclamation() {
        val originalAvailable = RobotInputsFramePool.availableCount
        
        // Rent frame
        val frame = RobotInputsFramePool.rent()
        frame.timestampMs = 999L
        frame.swerveInputs[0].driveVelocityRadsPerSec = 4.2
        
        // Renting should decrease pool size
        assertEquals(originalAvailable - 1, RobotInputsFramePool.availableCount)

        // Recycle frame
        RobotInputsFramePool.recycle(frame)
        
        // Recycling should restore pool size
        assertEquals(originalAvailable, RobotInputsFramePool.availableCount)
        
        // Rent again and verify the frame has been completely cleared
        val recycledFrame = RobotInputsFramePool.rent()
        assertEquals(0L, recycledFrame.timestampMs)
        assertEquals(0.0, recycledFrame.swerveInputs[0].driveVelocityRadsPerSec)
        
        // Return it back
        RobotInputsFramePool.recycle(recycledFrame)
    }

    @Test
    fun testInputLoggerAsynchronousFlushing() {
        val logger = InputLogger(tempFile)

        // Rent, populate, and log multiple frames
        val f1 = RobotInputsFramePool.rent().apply {
            timestampMs = 1000L
            swerveInputs[0].driveVelocityRadsPerSec = 1.0
            imuInputs.headingRadians = 0.5
        }
        logger.logFrame(f1)

        val f2 = RobotInputsFramePool.rent().apply {
            timestampMs = 2000L
            swerveInputs[0].driveVelocityRadsPerSec = 2.0
            imuInputs.headingRadians = 1.0
        }
        logger.logFrame(f2)

        // Stop the logger to flush and join worker thread
        logger.stop()

        // Read and verify log file
        assertTrue(tempFile.exists())
        val reader = BufferedReader(FileReader(tempFile))
        val lines = reader.readLines()
        reader.close()

        assertEquals(2, lines.size)

        // Parse first logged frame
        val loggedFrame1 = gson.fromJson(lines[0], RobotInputsFrame::class.java)
        assertEquals(1000L, loggedFrame1.timestampMs)
        assertEquals(1.0, loggedFrame1.swerveInputs[0].driveVelocityRadsPerSec)
        assertEquals(0.5, loggedFrame1.imuInputs.headingRadians)

        // Parse second logged frame
        val loggedFrame2 = gson.fromJson(lines[1], RobotInputsFrame::class.java)
        assertEquals(2000L, loggedFrame2.timestampMs)
        assertEquals(2.0, loggedFrame2.swerveInputs[0].driveVelocityRadsPerSec)
        assertEquals(1.0, loggedFrame2.imuInputs.headingRadians)
    }
}
