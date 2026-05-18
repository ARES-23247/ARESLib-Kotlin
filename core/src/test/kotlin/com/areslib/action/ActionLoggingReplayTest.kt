package com.areslib.action

import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.state.RobotState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionLoggingReplayTest {

    @Test
    fun testActionLoggerAndReplayDeterminism() {
        // Ensure logs directory is ready
        val logsDir = File("./logs")
        if (logsDir.exists()) {
            // Clean up pre-existing log files to avoid pollution
            logsDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("action_log_") && file.name.endsWith(".jsonl")) {
                    file.delete()
                }
            }
        }

        // 1. Initialize ActionLogger
        val logger = ActionLogger()

        // 2. Generate a stream of polymorphic actions
        val actions = listOf(
            RobotAction.DriveHardwareUpdate(
                xVelocity = 1.2,
                yVelocity = 0.5,
                angularVelocity = 0.1,
                deltaX = 0.02,
                deltaY = 0.01,
                deltaHeading = 0.002,
                timestampMs = 1000L
            ),
            RobotAction.PoseUpdate(
                xMeters = 0.5,
                yMeters = 0.2,
                headingRadians = 0.05,
                timestampMs = 1050L
            ),
            RobotAction.UpdateFlywheelRPM(
                rpm = 3800.0,
                timestampMs = 1100L
            ),
            RobotAction.SwitchPath(
                path = com.areslib.pathing.Path(
                    points = listOf(
                        com.areslib.pathing.PathPoint(Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 0.0),
                        com.areslib.pathing.PathPoint(Pose2d(1.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 1.0)
                    ),
                    events = emptyList()
                ),
                isDetour = false,
                timestampMs = 1150L
            ),
            RobotAction.UpdatePathProgress(
                distanceProgressMeters = 0.45,
                timestampMs = 1200L
            )
        )

        // 3. Log actions
        actions.forEach { logger.logAction(it) }

        // 4. Shut down logger to flush and close file writer cleanly
        logger.stop()

        // 5. Find the written JSONL log file
        assertTrue(logsDir.exists(), "Logs directory should exist")
        val logFiles = logsDir.listFiles()?.filter {
            it.name.startsWith("action_log_") && it.name.endsWith(".jsonl")
        } ?: emptyList()

        assertEquals(1, logFiles.size, "Should have created exactly one action log file")
        val writtenLogFile = logFiles.first()

        // 6. Perform manual linear reduction to calculate expected states
        val expectedStates = mutableListOf<RobotState>()
        var manualState = RobotState()
        expectedStates.add(manualState)
        actions.forEach { action ->
            manualState = com.areslib.reducer.rootReducer(manualState, action)
            expectedStates.add(manualState)
        }

        // 7. Replay using ActionReplay tool
        val replayedStates = ActionReplay.replayLog(writtenLogFile)

        // 8. Validate 100% mathematical determinism
        assertEquals(expectedStates.size, replayedStates.size, "State history sizes must match")
        for (i in expectedStates.indices) {
            val expected = expectedStates[i]
            val actual = replayedStates[i]

            // Assert drive velocities/odometry match
            assertEquals(expected.drive.xVelocityMetersPerSecond, actual.drive.xVelocityMetersPerSecond, 1e-6)
            assertEquals(expected.drive.yVelocityMetersPerSecond, actual.drive.yVelocityMetersPerSecond, 1e-6)
            assertEquals(expected.drive.angularVelocityRadiansPerSecond, actual.drive.angularVelocityRadiansPerSecond, 1e-6)
            assertEquals(expected.drive.odometryX, actual.drive.odometryX, 1e-6)
            assertEquals(expected.drive.odometryY, actual.drive.odometryY, 1e-6)
            assertEquals(expected.drive.odometryHeading, actual.drive.odometryHeading, 1e-6)

            // Assert superstructure states match
            assertEquals(expected.superstructure.flywheelRPM, actual.superstructure.flywheelRPM, 1e-6)

            // Assert path tracking states match
            assertEquals(expected.pathState.currentDistanceMeters, actual.pathState.currentDistanceMeters, 1e-6)
            assertEquals(expected.pathState.activePath?.points?.size, actual.pathState.activePath?.points?.size)
        }

        // Cleanup temporary test log file
        writtenLogFile.delete()
    }
}
