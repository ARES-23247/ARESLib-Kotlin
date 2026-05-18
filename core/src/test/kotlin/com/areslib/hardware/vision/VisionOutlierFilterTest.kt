package com.areslib.hardware.vision

import com.areslib.math.Pose2d
import com.areslib.math.Pose3d
import com.areslib.math.Rotation3d
import com.areslib.math.Translation3d
import com.areslib.state.VisionMeasurement
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VisionOutlierFilterTest {

    private val filter = VisionOutlierFilter()
    private val robotPose = Pose2d(0.0, 0.0)
    private val robotHeadingRad = 0.0

    @Test
    fun testValidMeasurement() {
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(2.0, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.05)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertTrue(filter.isValid(measurement, robotHeadingRad, robotPose))
    }

    @Test
    fun testDistanceRejection() {
        // Distance is 7.0 meters (exceeds max distance of 6.0)
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(7.0, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertFalse(filter.isValid(measurement, robotHeadingRad, robotPose))
    }

    @Test
    fun testAmbiguityRejection() {
        // Ambiguity is 0.3 (exceeds max of 0.2)
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(2.0, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.3
        )
        assertFalse(filter.isValid(measurement, robotHeadingRad, robotPose))
    }

    @Test
    fun testHeadingRejection() {
        // Heading deviation is 30 degrees (exceeds max of 15 degrees)
        val deviationRad = Math.toRadians(30.0)
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(2.0, 0.0, 0.0), Rotation3d(0.0, 0.0, deviationRad)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertFalse(filter.isValid(measurement, robotHeadingRad, robotPose))
    }
}
