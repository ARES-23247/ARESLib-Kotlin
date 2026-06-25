package com.areslib.telemetry

import com.areslib.state.RobotState
import com.areslib.hardware.HardwareRegistry
import com.areslib.hardware.MotorIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.io.File

class MockMotor(
    override var power: Double = 0.0,
    override var powerScale: Double = 1.0,
    override var velocity: Double = 0.0,
    override var position: Double = 0.0,
    override var currentAmps: Double = 0.0
) : MotorIO {
    override fun resetEncoder() {
        position = 0.0
    }
}

class FullStateLoggerTest {

    @Test
    fun testMotorTelemetryLoggingFix() {
        // Register mock motors
        val motorLf = MockMotor()
        val motorRf = MockMotor()
        HardwareRegistry.registerMotor("drive_lf", motorLf)
        HardwareRegistry.registerMotor("drive_rf", motorRf)

        val config = FullStateLogger.Config(
            logStates = true,
            logMotors = true,
            logVision = false,
            logFrequencyHz = 50
        )
        val runId = "run_test_logger"
        val logger = FullStateLogger(runId, "robot_test", 1, "BLUE", config)

        // Wait for logger to initialize
        Thread.sleep(100)

        val state = RobotState()

        // Log a few ticks
        for (i in 0..5) {
            logger.logTick(state, 12.0)
            logger.logMotorsTick(12.0)
            Thread.sleep(30)
        }

        // Close to flush
        logger.stop()

        // Verify that logs were generated
        val logsDir = File("./logs/")
        assertTrue(logsDir.exists())

        val motorLogFiles = logsDir.listFiles { _, name -> name.startsWith("motor_log_") && name.endsWith(".csv") }
        assertTrue(motorLogFiles != null && motorLogFiles.isNotEmpty(), "Motor log files should be generated")

        val latestMotorLog = motorLogFiles.maxByOrNull { it.lastModified() }!!
        val motorLines = latestMotorLog.readLines()

        // We expect at least header + some logged entries
        assertTrue(motorLines.size > 1, "Motor telemetry should contain at least headers and rows")
        
        val header = motorLines[0]
        assertEquals("run_id,robot_id,timestamp_ms,motor_id,voltage,current,temperature,position,velocity", header)

        // Clean up
        latestMotorLog.delete()
        
        val stateLogFiles = logsDir.listFiles { _, name -> name.startsWith("state_log_") && name.endsWith(".jsonl") }
        stateLogFiles?.forEach { it.delete() }
    }
}
