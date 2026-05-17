package com.areslib.math

import com.areslib.state.VisionMeasurement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PoseEstimatorTest {

    @Test
    fun testOdometryPropagation() {
        var state = PoseEstimatorState()
        
        // Add odometry reading (moved 1 meter forward)
        state = PoseEstimator.addOdometryObservation(
            state,
            100L,
            Translation2d(1.0, 0.0),
            Rotation2d(0.0)
        )
        
        assertEquals(1.0, state.estimatedPose.x, 1e-6)
        assertEquals(0.0, state.estimatedPose.y, 1e-6)
        assertEquals(0.0, state.estimatedPose.heading.radians, 1e-6)
        assertEquals(1, state.history.size)
    }

    @Test
    fun testVisionFusionRetroactive() {
        var state = PoseEstimatorState()
        
        // t=100: Odometry says we are at (1, 0)
        state = PoseEstimator.addOdometryObservation(
            state,
            100L,
            Translation2d(1.0, 0.0),
            Rotation2d(0.0)
        )
        
        // t=150: Odometry says we moved another 1 meter forward to (2, 0)
        state = PoseEstimator.addOdometryObservation(
            state,
            150L,
            Translation2d(1.0, 0.0),
            Rotation2d(0.0)
        )
        
        assertEquals(2.0, state.estimatedPose.x, 1e-6)
        
        // A vision measurement comes in with timestamp t=100.
        // It says we were actually at (1.5, 0) at t=100.
        // The EKF should pull the state at t=100 towards (1.5, 0).
        // Then it should re-apply the delta from t=100 to t=150, which is (+1, 0).
        // So the final estimated pose should be pulled towards (2.5, 0).
        
        val visionPose = Pose3d(Translation3d(1.5, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0))
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = visionPose,
            tagId = 1,
            ambiguity = 0.01
        )
        
        // Low vision std dev means we trust vision heavily
        state = PoseEstimator.addVisionMeasurement(
            state,
            measurement,
            Vector3(0.001, 0.001, 0.001)
        )
        
        // Expect x > 2.0 because vision pulled it forward.
        // With very low vision variance, it should be very close to 2.5
        assertTrue(state.estimatedPose.x > 2.1, "Pose X should be retroactively corrected forward: ${state.estimatedPose.x}")
        assertEquals(0.0, state.estimatedPose.y, 1e-6)
    }
}
