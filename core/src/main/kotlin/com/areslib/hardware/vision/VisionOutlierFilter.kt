package com.areslib.hardware.vision

import com.areslib.math.Pose2d
import com.areslib.math.Translation2d
import com.areslib.state.VisionMeasurement
import kotlin.math.abs

/**
 * Filter configuration thresholds for AprilTag measurements.
 */
data class VisionFilterConfig(
    val maxDistanceMeters: Double = 6.0,
    val maxAmbiguity: Double = 0.2,
    val maxRotationDeviationRad: Double = Math.toRadians(15.0)
)

/**
 * An outlier rejection filter that discards noisy, distant, or heading-deviating AprilTag vision measurements.
 */
class VisionOutlierFilter(val config: VisionFilterConfig = VisionFilterConfig()) {

    /**
     * Returns true if the vision measurement is physically valid and falls within all threshold constraints.
     */
    fun isValid(measurement: VisionMeasurement, robotHeadingRad: Double, robotPose: Pose2d): Boolean {
        // 1. Check Ambiguity (if >= 0.0)
        if (measurement.ambiguity > config.maxAmbiguity) {
            return false
        }

        // 2. Check Distance
        val tagPose2d = measurement.targetPose.toPose2d()
        val dx = tagPose2d.x - robotPose.x
        val dy = tagPose2d.y - robotPose.y
        val distance = Translation2d(dx, dy).norm

        if (distance > config.maxDistanceMeters) {
            return false
        }

        // 3. Check Yaw rotation alignment relative to robot gyro heading
        val tagYaw = measurement.targetPose.rotation.z
        val headingDiff = normalizeAngle(tagYaw - robotHeadingRad)

        if (abs(headingDiff) > config.maxRotationDeviationRad) {
            return false
        }

        return true
    }

    /**
     * Normalizes an angle in radians to the range (-PI, PI].
     */
    private fun normalizeAngle(angle: Double): Double {
        var a = angle
        while (a <= -Math.PI) a += 2 * Math.PI
        while (a > Math.PI) a -= 2 * Math.PI
        return a
    }
}
