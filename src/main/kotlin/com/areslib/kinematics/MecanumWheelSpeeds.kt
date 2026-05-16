package com.areslib.kinematics

import kotlin.math.abs

/**
 * Pure data class representing the four wheel speeds of a Mecanum drive.
 */
data class MecanumWheelSpeeds(
    val frontLeftMetersPerSecond: Double = 0.0,
    val frontRightMetersPerSecond: Double = 0.0,
    val backLeftMetersPerSecond: Double = 0.0,
    val backRightMetersPerSecond: Double = 0.0
) {
    /**
     * Normalizes the wheel speeds if any of them exceed the specified maximum speed.
     * Returns a new immutable MecanumWheelSpeeds.
     */
    fun normalize(maxSpeedMetersPerSecond: Double): MecanumWheelSpeeds {
        val maxMagnitude = maxOf(
            abs(frontLeftMetersPerSecond),
            abs(frontRightMetersPerSecond),
            abs(backLeftMetersPerSecond),
            abs(backRightMetersPerSecond)
        )
        
        if (maxMagnitude > maxSpeedMetersPerSecond) {
            val scale = maxSpeedMetersPerSecond / maxMagnitude
            return MecanumWheelSpeeds(
                frontLeftMetersPerSecond * scale,
                frontRightMetersPerSecond * scale,
                backLeftMetersPerSecond * scale,
                backRightMetersPerSecond * scale
            )
        }
        
        return this
    }
}
