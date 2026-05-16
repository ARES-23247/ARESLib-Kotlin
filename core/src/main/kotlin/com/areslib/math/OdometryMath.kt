package com.areslib.math

import kotlin.math.cos
import kotlin.math.sin

object OdometryMath {
    /**
     * Calculates new pose given delta positions from dead wheels (forward and strafe).
     * Simplified for differential / pure holonomic.
     */
    fun calculateDeltaPose(
        currentHeadingRadians: Double,
        deltaForwardMeters: Double,
        deltaStrafeMeters: Double
    ): Translation2d {
        // Rotate the robot-centric deltas by the current heading to get field-centric deltas
        val cosH = cos(currentHeadingRadians)
        val sinH = sin(currentHeadingRadians)
        
        val deltaX = deltaForwardMeters * cosH - deltaStrafeMeters * sinH
        val deltaY = deltaForwardMeters * sinH + deltaStrafeMeters * cosH
        
        return Translation2d(deltaX, deltaY)
    }
}
