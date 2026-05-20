package com.areslib.e2e.tier1.state

import com.areslib.action.RobotAction
import com.areslib.reducer.*
import com.areslib.state.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReducerSafetyTier1Test {

    @Test
    fun testRootReducer_shouldReturnIdenticalStateOnUnrecognizedAction() {
        val originalState = RobotState(timestampMs = 100L)
        // PathEventTriggered is not handled by Drive, Vision, Path, or Costmap, and doesn't change Superstructure if name is dummy
        val invalidAction = RobotAction.PathEventTriggered("DummyEvent", 100L)

        val resultState = rootReducer(originalState, invalidAction)
        
        // Since rootReducer calls state.copy(timestampMs = action.timestampMs), it returns a new RobotState wrapper.
        // But the sub-states inside must be referentially identical.
        assertSame(originalState.drive, resultState.drive)
        assertSame(originalState.superstructure, resultState.superstructure)
        assertSame(originalState.vision, resultState.vision)
        assertSame(originalState.pathState, resultState.pathState)
        assertSame(originalState.costmap, resultState.costmap)
    }

    @Test
    fun testSubReducers_shouldReturnPreviousStateOnUnrecognizedAction() {
        val driveState = DriveState()
        val invalidAction = RobotAction.PathEventTriggered("DummyEvent", 100L)
        assertSame(driveState, DriveReducer.reduce(driveState, invalidAction))

        val superstructureState = SuperstructureState()
        assertSame(superstructureState, SuperstructureReducer.reduce(superstructureState, invalidAction))

        val visionState = VisionState()
        assertSame(visionState, VisionReducer.reduce(visionState, invalidAction))

        val pathState = PathState()
        assertSame(pathState, PathReducer.reduce(pathState, invalidAction))

        val costmapState = CostmapState()
        assertSame(costmapState, CostmapReducer.reduce(costmapState, invalidAction, com.areslib.math.Pose2d()))
    }

    @Test
    fun testCostmapReducer_shouldSafelyCopyWorkingLists() {
        val originalCostmap = CostmapState()
        val observation = RobotAction.DistanceSensorObservation(
            sensorId = "front",
            angleOffsetRad = 0.0,
            positionOffsetXMeters = 0.0,
            positionOffsetYMeters = 0.0,
            distanceMeters = 1.0,
            maxRangeMeters = 4.0
        )
        val addObstacleAction = RobotAction.ObstacleCostmapUpdate(listOf(observation), 100L)

        val updatedCostmap = CostmapReducer.reduce(originalCostmap, addObstacleAction, com.areslib.math.Pose2d())
        
        assertNotSame(originalCostmap, updatedCostmap)
        assertEquals(1, updatedCostmap.obstacles.size)
        assertEquals(1.0, updatedCostmap.obstacles[0].x, 1e-6)
        assertEquals(0.0, updatedCostmap.obstacles[0].y, 1e-6)
        assertEquals(0.2, updatedCostmap.obstacles[0].radius, 1e-6)
    }

    @Test
    fun testSuperstructureReducer_invalidModes_shouldGracefullyDiscard() {
        val originalState = SuperstructureState()
        
        // Set transfer active is only valid if flywheel is ready/at speed and inventory > 0.
        // Let's test that if we dispatch it while idle, it safely handles it without throwing.
        val transferAction = RobotAction.SetTransferActive(active = true, timestampMs = 100L)
        val resultState = SuperstructureReducer.reduce(originalState, transferAction)
        
        // Should not throw, and should maintain healthy state limits
        assertNotNull(resultState)
        assertEquals(SuperstructureMode.IDLE, resultState.mode)
        assertFalse(resultState.transferActive)
    }
}
