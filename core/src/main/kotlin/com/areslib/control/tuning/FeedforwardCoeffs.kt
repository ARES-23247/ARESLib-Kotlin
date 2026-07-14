package com.areslib.control.tuning

/**
 * Reusable data class holding feedforward constants for a simple voltage-controlled mechanism 
 * (like a flywheel, drivetrain, or simple intake).
 */
data class SimpleFeedforwardCoeffs(
    val kS: Double = 0.0,
    val kV: Double = 0.0,
    val kA: Double = 0.0
)

/**
 * Reusable data class holding feedforward constants for an elevator mechanism fighting constant gravity.
 */
data class ElevatorFeedforwardCoeffs(
    val kS: Double = 0.0,
    val kG: Double = 0.0,
    val kV: Double = 0.0,
    val kA: Double = 0.0
)

/**
 * Reusable data class holding feedforward constants for a pivoting arm fighting variable gravity.
 */
data class ArmFeedforwardCoeffs(
    val kS: Double = 0.0,
    val kG: Double = 0.0,
    val kV: Double = 0.0,
    val kA: Double = 0.0
)
