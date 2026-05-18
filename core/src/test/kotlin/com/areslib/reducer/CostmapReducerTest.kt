package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.state.RobotState
import com.areslib.state.DriveState
import com.areslib.math.PoseEstimatorState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostmapReducerTest {

    @Test
    fun `test distance sensor observation projects correctly to field coordinates`() {
        // Robot at Pose(x=1.0, y=2.0, heading=0)
        // Sensor mounted at offsetX=0.5, offsetY=0.0, angle=0
        // Measures distance = 1.0.
        // Expected obstacle position: x = 1.0 + 0.5 + 1.0 = 2.5, y = 2.0.
        val initialPose = Pose2d(1.0, 2.0, Rotation2d.fromDegrees(0.0))
        val state = RobotState(
            drive = DriveState(
                poseEstimator = PoseEstimatorState(estimatedPose = initialPose)
            )
        )

        val observation = RobotAction.DistanceSensorObservation(
            sensorId = "front",
            angleOffsetRad = 0.0,
            positionOffsetXMeters = 0.5,
            positionOffsetYMeters = 0.0,
            distanceMeters = 1.0,
            maxRangeMeters = 4.0
        )

        val action = RobotAction.ObstacleCostmapUpdate(
            observations = listOf(observation),
            timestampMs = 1000L
        )

        val nextState = rootReducer(state, action)
        
        assertEquals(1, nextState.costmap.obstacles.size)
        val obstacle = nextState.costmap.obstacles.first()
        assertEquals(2.5, obstacle.x, 0.001)
        assertEquals(2.0, obstacle.y, 0.001)
    }

    @Test
    fun `test max range observation prunes obstacles in line of sight`() {
        val initialPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        val state = RobotState(
            drive = DriveState(
                poseEstimator = PoseEstimatorState(estimatedPose = initialPose)
            )
        )

        // Step 1: Detect obstacle at (1.0, 0.0)
        val obs1 = RobotAction.DistanceSensorObservation(
            sensorId = "front",
            angleOffsetRad = 0.0,
            positionOffsetXMeters = 0.0,
            positionOffsetYMeters = 0.0,
            distanceMeters = 1.0,
            maxRangeMeters = 4.0
        )
        val stateWithObstacle = rootReducer(
            state,
            RobotAction.ObstacleCostmapUpdate(listOf(obs1), 1000L)
        )
        assertEquals(1, stateWithObstacle.costmap.obstacles.size)

        // Step 2: Send clear observation from front sensor (reports maxRange=4.0)
        val obsClear = RobotAction.DistanceSensorObservation(
            sensorId = "front",
            angleOffsetRad = 0.0,
            positionOffsetXMeters = 0.0,
            positionOffsetYMeters = 0.0,
            distanceMeters = 4.0, // equal to max range -> indicates clear
            maxRangeMeters = 4.0
        )
        val stateCleared = rootReducer(
            stateWithObstacle,
            RobotAction.ObstacleCostmapUpdate(listOf(obsClear), 1001L)
        )

        // Obstacle at (1.0, 0.0) is along the sensor ray (angle = 0, dist = 1.0 < 4.0).
        // It should be pruned!
        assertTrue(stateCleared.costmap.obstacles.isEmpty())
    }
}
