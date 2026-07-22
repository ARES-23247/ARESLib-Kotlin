package com.areslib.math.kinematics

import com.areslib.math.geometry.*

import kotlin.math.cos
import kotlin.math.sin

/**
 * Object implementation for Odometry Math.
 *
 * Provides mathematical state estimation, vector filtering, or kinematic matrix operations.
 *
 * ### Physical Units & Coordinates:
 * - Position: Meters ($m$)
 * - Heading: Radians ($rad$), counter-clockwise positive
 * - Time: Seconds ($s$) or milliseconds ($ms$)
 */
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
