package com.areslib.math

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure math utilities for target aiming and velocity compensation.
 */
object AimingMath {

    /**
     * Calculates the required robot heading to hit a target while moving.
     * Combines the robot's coordinate offset and field-relative velocity vector
     * to solve for the time of flight and relative launching angle.
     *
     * @param robotX Robot's current X position on the field (meters)
     * @param robotY Robot's current Y position on the field (meters)
     * @param vx Robot's current field-relative X velocity (m/s)
     * @param vy Robot's current field-relative Y velocity (m/s)
     * @param targetX High goal X coordinate (meters)
     * @param targetY High goal Y coordinate (meters)
     * @param shotSpeed Ball exit velocity relative to the shooter (m/s)
     * @return The required robot heading in radians (CCW-positive)
     */
    fun calculateCompensatedHeading(
        robotX: Double,
        robotY: Double,
        vx: Double,
        vy: Double,
        targetX: Double,
        targetY: Double,
        shotSpeed: Double
    ): Double {
        val dx = targetX - robotX
        val dy = targetY - robotY
        val distanceSq = dx * dx + dy * dy
        val robotSpeedSq = vx * vx + vy * vy
        
        // Dot product of distance vector and robot velocity vector
        val p = dx * vx + dy * vy
        
        val a = shotSpeed * shotSpeed - robotSpeedSq
        val b = 2.0 * p
        val c = -distanceSq
        
        val discriminant = b * b - 4.0 * a * c
        if (discriminant < 0.0 || a <= 0.0) {
            // Fallback: Geometric aiming (no compensation) if no real solution
            return atan2(dy, dx)
        }
        
        // Solve for time of flight (positive root)
        val t = (-b + sqrt(discriminant)) / (2.0 * a)
        if (t <= 0.0) {
            return atan2(dy, dx)
        }
        
        // Calculate the compensated launch vector in the robot frame
        val launchVx = (dx / t) - vx
        val launchVy = (dy / t) - vy
        
        return atan2(launchVy, launchVx)
    }
}
