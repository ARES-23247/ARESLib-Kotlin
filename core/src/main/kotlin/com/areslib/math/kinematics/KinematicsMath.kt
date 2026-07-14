package com.areslib.math.kinematics

import kotlin.math.sqrt

/**
 * Pure math utility for 1D kinematic equations.
 */
object KinematicsMath {
    
    /**
     * Calculates the final velocity given an initial velocity, a constant acceleration, and a distance traveled.
     * Uses the 1D kinematics equation: vf^2 = vi^2 + 2ad
     * 
     * @param initialVelocity The starting velocity
     * @param acceleration The constant acceleration
     * @param distance The distance traveled
     * @return The final velocity. If (vi^2 + 2ad) is negative due to float precision, returns 0.0.
     */
    fun finalVelocity(initialVelocity: Double, acceleration: Double, distance: Double): Double {
        val v2 = initialVelocity * initialVelocity + 2.0 * acceleration * distance
        return if (v2 <= 0.0) 0.0 else sqrt(v2)
    }
}
