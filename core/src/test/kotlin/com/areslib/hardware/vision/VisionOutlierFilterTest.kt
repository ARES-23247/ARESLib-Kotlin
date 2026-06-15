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
        // Heading deviation is 35 degrees (exceeds max of 30 degrees)
        val deviationRad = Math.toRadians(35.0)
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(2.0, 0.0, 0.0), Rotation3d(0.0, 0.0, deviationRad)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertFalse(filter.isValid(measurement, robotHeadingRad, robotPose))
    }

    @Test
    fun test3DFieldBoundaryRejections() {
        // X out of bounds (> 2.5)
        val outOfBoundsX = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(2.6, 0.0, 0.2), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertFalse(filter.isValid(outOfBoundsX, robotHeadingRad, robotPose))

        // Y out of bounds (< -2.5)
        val outOfBoundsY = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(0.0, -2.6, 0.2), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertFalse(filter.isValid(outOfBoundsY, robotHeadingRad, robotPose))

        // Z underground (< -0.2)
        val undergroundZ = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(0.0, 0.0, -0.25), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertFalse(filter.isValid(undergroundZ, robotHeadingRad, robotPose))

        // Z floating (> 1.0)
        val floatingZ = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(0.0, 0.0, 1.05), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.05
        )
        assertFalse(filter.isValid(floatingZ, robotHeadingRad, robotPose))
    }

    @Test
    fun testAngularVelocityBlurLockout() {
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(1.0, 0.0, 0.2), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.05
        )

        // Spin too fast (2.1 rad/s > 2.0 rad/s limit)
        assertFalse(filter.isValid(measurement, robotHeadingRad, robotPose, angularVelocityRadPerSec = 2.1))
        
        // Spin under limit (1.9 rad/s <= 2.0 rad/s limit)
        assertTrue(filter.isValid(measurement, robotHeadingRad, robotPose, angularVelocityRadPerSec = 1.9))
    }

    @Test
    fun testHighGShockCollisionLockout() {
        val measurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(1.0, 0.0, 0.2), Rotation3d(0.0, 0.0, 0.0)),
            tagId = 1,
            ambiguity = 0.05
        )

        // Rest case (1G Z gravity, 0G lateral) should pass
        assertTrue(filter.isValid(measurement, robotHeadingRad, robotPose, linearAccelXG = 0.0, linearAccelYG = 0.0, linearAccelZG = 1.0))

        // Dynamic shock in X/Y exceeding 2.5G: X=2.6G should fail
        assertFalse(filter.isValid(measurement, robotHeadingRad, robotPose, linearAccelXG = 2.6, linearAccelYG = 0.0, linearAccelZG = 1.0))

        // Dynamic shock in Z exceeding 2.5G: Z=3.6G (dynamic Z = 2.6G) should fail
        assertFalse(filter.isValid(measurement, robotHeadingRad, robotPose, linearAccelXG = 0.0, linearAccelYG = 0.0, linearAccelZG = 3.6))

        // Under 2.5G limit (e.g. 2.0G dynamic shock) should pass
        assertTrue(filter.isValid(measurement, robotHeadingRad, robotPose, linearAccelXG = 2.0, linearAccelYG = 0.0, linearAccelZG = 1.0))
    }

    @Test
    fun testFrcDefaults() {
        val frcFilter = VisionOutlierFilter(VisionFilterConfig.frcDefaults())
        
        // Measurement that exceeds FTC limits (e.g. X = 8.0m, Z = 2.0m, distance = 8.24m, yaw deviation = 20 deg)
        // but is valid within FRC presets.
        val frcMeasurement = VisionMeasurement(
            timestampMs = 100L,
            targetPose = Pose3d(Translation3d(8.0, 2.0, 2.0), Rotation3d(0.0, 0.0, Math.toRadians(20.0))),
            tagId = 5,
            ambiguity = 0.05
        )
        
        val robotPose = Pose2d(0.0, 0.0)
        
        // Verify it passes FRC filter
        assertTrue(frcFilter.isValid(
            frcMeasurement, 
            robotHeadingRad = 0.0, 
            robotPose = robotPose,
            angularVelocityRadPerSec = 1.0,
            linearAccelXG = 1.0,
            linearAccelYG = 1.0,
            linearAccelZG = 1.0
        ))
        
        // Verify it fails standard (FTC) filter
        assertFalse(filter.isValid(
            frcMeasurement, 
            robotHeadingRad = 0.0, 
            robotPose = robotPose,
            angularVelocityRadPerSec = 1.0,
            linearAccelXG = 1.0,
            linearAccelYG = 1.0,
            linearAccelZG = 1.0
        ))
    }
}
