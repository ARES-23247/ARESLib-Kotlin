package com.areslib.control.tuning

/**
 * Reusable data class holding PIDF gains for any closed-loop controller.
 */
data class PIDFCoefficients(
    val kP: Double = 0.0,
    val kI: Double = 0.0,
    val kD: Double = 0.0,
    val kF: Double = 0.0
)
