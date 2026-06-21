package com.areslib.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d
import com.areslib.math.Translation3d
import com.areslib.math.Rotation3d
import com.areslib.math.Vector3
import com.areslib.subsystem.Store
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockVisionIO(var mockMeasurements: List<VisionMeasurement> = emptyList()) : VisionIO {
    val isConnected: Boolean = true
    override fun updateInputs(inputs: VisionIOInputs) {
        inputs.isConnected = isConnected
        inputs.measurements = mockMeasurements
    }
}

class FtcVisionTrackerTest {
    @Test
    fun `test initial alignment snap`() {
        val store = Store(RobotState(), ::rootReducer)
        val mockMeasurement = VisionMeasurement(
            tagId = 3,
            targetPose = Pose3d(Translation3d(1.0, 2.0, 0.0), Rotation3d(0.0, 0.0, 0.5)), // 0.5 yaw rad
            ambiguity = 0.01,
            timestampMs = 100
        )
        val visionIO = MockVisionIO(listOf(mockMeasurement))
        val tracker = FtcVisionTracker(store, visionIO, pinpointIO = null)

        tracker.update(100)

        // EKF pose estimator should have snapped to tag pose
        val estPose = store.state.drive.poseEstimator.estimatedPose
        assertEquals(1.0, estPose.x, 1e-6)
        assertEquals(2.0, estPose.y, 1e-6)
        assertEquals(0.5, estPose.heading.radians, 1e-6)
        assertEquals("INIT_ALIGN_SNAP", tracker.lastVisionStatus)
    }

    @Test
    fun `test tag ambiguity rejection`() {
        val store = Store(RobotState(), ::rootReducer)
        // High ambiguity tag (> maxAmbiguity, which defaults to 0.15)
        val mockMeasurement = VisionMeasurement(
            tagId = 3,
            targetPose = Pose3d(Translation3d(1.0, 2.0, 0.0), Rotation3d(0.0, 0.0, 0.5)),
            ambiguity = 0.5,
            timestampMs = 100
        )
        val visionIO = MockVisionIO(listOf(mockMeasurement))
        val tracker = FtcVisionTracker(store, visionIO, pinpointIO = null)

        tracker.update(100)

        // It should be rejected and EKF pose remains default (0, 0, 0)
        val estPose = store.state.drive.poseEstimator.estimatedPose
        assertEquals(0.0, estPose.x, 1e-6)
        assertTrue(tracker.lastVisionStatus.startsWith("REJ_AMBIG"))
    }
}
