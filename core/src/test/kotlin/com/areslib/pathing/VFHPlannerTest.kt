package com.areslib.pathing

import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.state.Obstacle
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.PI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VFHPlannerTest {

    @Test
    fun `test no obstacles returns target heading`() {
        val planner = VFHPlanner()
        val robotPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        val heading = planner.computeDetourHeading(robotPose, 0.5, emptyList())
        assertEquals(0.5, heading, 0.001)
    }

    @Test
    fun `test obstacle directly in front causes detour steering`() {
        val planner = VFHPlanner(sensingRangeMeters = 3.0, safetyThreshold = 0.1)
        val robotPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        
        // Target heading is directly forward (0.0 rad)
        val targetHeading = 0.0
        
        // Place an obstacle at (1.0, 0.0) -> directly in front
        val obstacles = listOf(Obstacle(1.0, 0.0))
        
        val detourHeading = planner.computeDetourHeading(robotPose, targetHeading, obstacles)
        
        // Detour heading should steer away from 0.0 radians
        assertTrue(abs(detourHeading) > 0.1, "Should have steered away from the obstacle in front: $detourHeading")
    }
}
