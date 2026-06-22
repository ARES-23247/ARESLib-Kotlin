package com.areslib.fsm

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.pathing.Path
import com.areslib.pathing.PathPoint
import com.areslib.reducer.rootReducer
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import com.areslib.subsystem.Store
import com.areslib.util.asFlow
import com.areslib.util.waitUntil
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CoroutineAutonomousTest {

    @Test
    fun testLinearCoroutineAutonomousSequence() = runBlocking {
        val store = Store(initialState = RobotState(), reducer = ::rootReducer)
        val stateFlow = store.asFlow()

        val baseTime = 1000L
        
        // Define path points
        val path = Path(listOf(
            PathPoint(Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 0.0),
            PathPoint(Pose2d(1.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 1.0),
            PathPoint(Pose2d(2.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 2.0)
        ), emptyList())

        // Set initial inventory to 1 ball
        store.dispatch(RobotAction.SetInventoryCount(1, baseTime))
        assertEquals(1, store.state.superstructure.inventoryCount)

        // Launch simulated physics/hardware loop concurrently
        val physicsJob = launch {
            var currentTimestamp = baseTime
            while (isActive) {
                delay(20) // 50 Hz loop simulation
                currentTimestamp += 20L
                
                val currentState = store.state
                
                // 1. Simulate path progress increment if path is active
                if (currentState.pathState.activePath != null) {
                    val nextProgress = currentState.pathState.currentDistanceMeters + 0.15
                    store.dispatch(RobotAction.UpdatePathProgress(nextProgress, currentTimestamp))
                }

                // 2. Simulate flywheel ramping
                if (currentState.superstructure.flywheelActive) {
                    val currentRpm = currentState.superstructure.flywheelRPM
                    val nextRpm = (currentRpm + 800.0).coerceAtMost(4000.0)
                    store.dispatch(RobotAction.UpdateFlywheelRPM(nextRpm, currentTimestamp))
                }

                // 3. Simulate transfer expelling ball
                if (currentState.superstructure.transferActive && currentState.superstructure.inventoryCount > 0) {
                    store.dispatch(RobotAction.SetInventoryCount(0, currentTimestamp))
                }
            }
        }

        // Autonomous script execution (runs sequentially)
        val scriptJob = launch {
            // Step 1: Switch path
            store.dispatch(RobotAction.SwitchPath(path, isDetour = false, timestampMs = baseTime))
            assertNotNull(store.state.pathState.activePath)

            // Step 2: Wait until the robot drives at least 0.5 meters
            val wait1Success = stateFlow.waitUntil(timeoutMs = 2000L) { state ->
                state.pathState.currentDistanceMeters >= 0.5
            }
            assertTrue(wait1Success, "Wait for distance progress should have succeeded")
            assertTrue(store.state.pathState.currentDistanceMeters >= 0.5)

            // Step 3: Spin up flywheel
            store.dispatch(RobotAction.SetFlywheelActive(active = true, timestampMs = baseTime + 200))
            assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, store.state.superstructure.mode)

            // Step 4: Wait until the flywheel is ready (RPM >= 95% of target 4000)
            val wait2Success = stateFlow.waitUntil(timeoutMs = 2000L) { state ->
                state.superstructure.isFlywheelAtSpeed
            }
            assertTrue(wait2Success, "Wait for flywheel ready should have succeeded")
            assertTrue(store.state.superstructure.isFlywheelAtSpeed)

            // Step 5: Feed and shoot (activate transfer)
            store.dispatch(RobotAction.SetTransferActive(active = true, timestampMs = baseTime + 400))
            
            // Wait for inventory count to become 0
            val wait3Success = stateFlow.waitUntil(timeoutMs = 2000L) { state ->
                state.superstructure.inventoryCount == 0
            }
            assertTrue(wait3Success, "Wait for shooting to empty inventory should have succeeded")
            assertEquals(0, store.state.superstructure.inventoryCount)

            // Step 6: Disable superstructure shooters
            store.dispatch(RobotAction.SetTransferActive(active = false, timestampMs = baseTime + 600))
            store.dispatch(RobotAction.SetFlywheelActive(active = false, timestampMs = baseTime + 600))
            assertEquals(SuperstructureMode.IDLE, store.state.superstructure.mode)
        }

        // Wait for the autonomous script execution to complete
        scriptJob.join()

        // Clean up the physics background loop
        physicsJob.cancelAndJoin()

        // Final verifications
        assertFalse(store.state.superstructure.transferActive)
        assertFalse(store.state.superstructure.flywheelActive)
        assertEquals(0, store.state.superstructure.inventoryCount)
    }

    @Test
    fun testWaitUntilTimeoutHandling() = runBlocking {
        val store = Store(initialState = RobotState(), reducer = ::rootReducer)
        val stateFlow = store.asFlow()

        // Wait for a condition that will never happen (e.g. inventory count = 99)
        val waitSuccess = withTimeoutOrNull(500L) {
            stateFlow.waitUntil(timeoutMs = 200L) { state ->
                state.superstructure.inventoryCount == 99
            }
        }
        // Result should be false because of the 200ms inner timeout (not null from the outer 500ms block)
        assertEquals(false, waitSuccess)
    }
}
