package com.areslib.ftc

import com.areslib.action.RobotAction
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.fsm.*
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.pathing.*
import com.areslib.reducer.rootReducer
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class AutonomousE2ETest {

    @Test
    fun testCompleteAutonomousChainingDetourAndFSMSequence() {
        println("=== STARTING AUTONOMOUS E2E SIMULATION VALIDATION ===")
        
        // 1. Initialize complete system states and sequencers
        var state = RobotState()
        val taskExecutor = TaskExecutor()
        
        val driveController = HolonomicDriveController(
            xController = PIDController(4.0, 0.0, 0.1),
            yController = PIDController(4.0, 0.0, 0.1),
            thetaController = PIDController(3.0, 0.0, 0.0)
        )

        val baseTime = 1000L

        // 2. Define path segments & chain them together (trajectory stitching)
        val pathA = Path(listOf(
            PathPoint(Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 0.0),
            PathPoint(Pose2d(1.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 1.0),
            PathPoint(Pose2d(2.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 2.0)
        ), emptyList())

        val pathB = Path(listOf(
            PathPoint(Pose2d(2.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 0.0),
            PathPoint(Pose2d(3.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 1.0),
            PathPoint(Pose2d(4.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 2.0)
        ), emptyList())

        val pathChained = PathChainer.chainPaths(
            listOf(pathA, pathB),
            maxVelocityMps = 2.0,
            maxAccelerationMps2 = 1.5
        )

        assertNotNull(pathChained)
        assertTrue(pathChained.points.size > 4)

        // Initialize active path via Redux SwitchPath
        state = rootReducer(state, RobotAction.SwitchPath(pathChained, isDetour = false, timestampMs = baseTime))
        assertNotNull(state.pathState.activePath)

        // 3. Queue sequence of conditional FSM tasks:
        //    a. Wait until path progress is at least 0.2 meters
        //    b. Spin up flywheel (4000 RPM) and wait until ready
        //    c. Feed to shooter (ShootTask)
        //    d. Spin up intake until 1 ball secured
        taskExecutor.addTask(PathProgressWaitTask(0.2))
        taskExecutor.addTask(FlywheelReadyTask(4000.0, baseTime))
        taskExecutor.addTask(ShootTask(baseTime))
        taskExecutor.addTask(IntakeUntilCountTask(1, baseTime))

        // Set initial inventory to 1 ball
        state = rootReducer(state, RobotAction.SetInventoryCount(1, baseTime))

        var detourTriggered = false

        // 4. Run time-step simulation (60 loops of 20ms = 1.2s total execution window)
        for (step in 1..60) {
            val currentTimestamp = baseTime + step * 20L
            val currentPose = state.drive.poseEstimator.estimatedPose
            val currentDistance = state.pathState.currentDistanceMeters

            val activePath = state.pathState.activePath ?: break
            val targetState = activePath.sampleAtDistance(currentDistance)

            // Closed-loop Holonomic Drive Controller execution
            val speeds = driveController.calculate(
                currentPose = currentPose,
                targetPose = targetState.pose,
                targetVelocityMps = targetState.velocityMps,
                targetHeading = targetState.pose.heading,
                dtSeconds = 0.02
            )

            // Dispatch motion update to update poseEstimator state
            val deltaX = speeds.vxMetersPerSecond * 0.02
            val deltaY = speeds.vyMetersPerSecond * 0.02
            val deltaHeading = speeds.omegaRadiansPerSecond * 0.02
            val driveUpdate = RobotAction.DriveHardwareUpdate(
                xVelocity = speeds.vxMetersPerSecond,
                yVelocity = speeds.vyMetersPerSecond,
                angularVelocity = speeds.omegaRadiansPerSecond,
                deltaX = deltaX,
                deltaY = deltaY,
                deltaHeading = deltaHeading,
                timestampMs = currentTimestamp
            )
            state = rootReducer(state, driveUpdate)

            // Update trajectory progress (coerced nominal velocity to prevent start/end 0Mps profile standstills)
            val nominalSpeed = if (targetState.velocityMps < 0.1) 1.5 else targetState.velocityMps
            val nextProgress = currentDistance + 0.02 * nominalSpeed
            state = rootReducer(state, RobotAction.UpdatePathProgress(nextProgress, currentTimestamp))

            // 5. Dynamic Detour Triggering
            //    Senses costmap obstacle ahead at x = 0.3 (detour around obstacle)
            if (currentPose.x >= 0.3 && !detourTriggered) {
                println(">>> [DETOUR] Sensed costmap obstacle at x=0.3! Triggering dynamic detour spline generator.")
                val detourPath = Path(listOf(
                    PathPoint(Pose2d(1.0, 0.5, Rotation2d.fromDegrees(0.0)), 1.5, 0.0),
                    PathPoint(Pose2d(2.0, 0.5, Rotation2d.fromDegrees(0.0)), 1.5, 1.0),
                    PathPoint(Pose2d(3.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 2.0)
                ), emptyList())

                val smoothDetour = DetourGenerator.generateTangentArc(
                    startPose = currentPose,
                    currentVelMps = targetState.velocityMps,
                    targetPath = detourPath,
                    interceptLookaheadMeters = 0.3
                )

                state = rootReducer(state, RobotAction.SwitchPath(smoothDetour, isDetour = true, timestampMs = currentTimestamp))
                detourTriggered = true
                assertTrue(state.pathState.detourActive)
            }

            // 6. Simulate physical superstructure dynamics in loop
            //    Ramp up flywheel when active
            if (state.superstructure.flywheelActive) {
                val currentRpm = state.superstructure.flywheelRPM
                val nextRpm = (currentRpm + 600.0).coerceAtMost(4000.0)
                state = rootReducer(state, RobotAction.UpdateFlywheelRPM(nextRpm, currentTimestamp))
            }

            //    Simulate shooting/expelling once transfer goes active
            if (state.superstructure.transferActive && state.superstructure.inventoryCount > 0) {
                // Ball shot!
                state = rootReducer(state, RobotAction.SetInventoryCount(0, currentTimestamp))
                println(">>> [SHOOTER] Transfer motor fed ball to flywheel! Inventory is now empty.")
            }

            //    Simulate intake picking up ball once intake goes active
            if (state.superstructure.intakeActive && state.superstructure.inventoryCount == 0) {
                // Ball intaked!
                state = rootReducer(state, RobotAction.SetInventoryCount(1, currentTimestamp))
                println(">>> [INTAKE] Intake motor active! Secured 1 ball.")
            }

            // Update FSM Task Executor sequencer
            val fsmActions = taskExecutor.update(state, currentTimestamp)
            fsmActions.forEach { action ->
                state = rootReducer(state, action)
            }

            println("Step $step | Pose=(${String.format("%.3f", state.drive.poseEstimator.estimatedPose.x)}, ${String.format("%.3f", state.drive.poseEstimator.estimatedPose.y)}), Heading=${String.format("%.1f", Math.toDegrees(state.drive.poseEstimator.estimatedPose.heading.radians))} | Distance=${String.format("%.3f", state.pathState.currentDistanceMeters)} | ActiveTask=${taskExecutor.activeTaskName} | Mode=${state.superstructure.mode} | FlywheelRPM=${state.superstructure.flywheelRPM} | Inventory=${state.superstructure.inventoryCount}")
        }

        println("=== END OF AUTONOMOUS E2E SIMULATION VALIDATION ===")

        // 7. Verify E2E Scenario Accomplishments
        assertTrue(detourTriggered, "Autonomous loop should have triggered detour switching")
        
        // Assert FSM completed all tasks
        assertEquals(0, taskExecutor.size, "FSM Task Executor should have successfully executed all tasks in queue")
        assertNull(taskExecutor.activeTaskName)

        // Verify state variables at end of autonomous sequence
        assertFalse(state.superstructure.transferActive, "Transfer should have been disabled after shooting")
        assertFalse(state.superstructure.intakeActive, "Intake should have shut down once inventory target reached")
        assertEquals(1, state.superstructure.inventoryCount, "Inventory count should be 1 ball after intake completes")
    }
}
