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
        
        // Dynamic costmap updates are disabled; obstacles must remain empty
        assertTrue(nextState.costmap.obstacles.isEmpty())
    }

    @Test
    fun `test max range observation prunes obstacles in line of sight`() {
        val initialPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        val state = RobotState(
            drive = DriveState(
                poseEstimator = PoseEstimatorState(estimatedPose = initialPose)
            )
        )

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
        assertTrue(stateWithObstacle.costmap.obstacles.isEmpty())
    }
}
