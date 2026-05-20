package com.areslib.control

import kotlin.math.cos

/**
 * Clean, high-performance gravity feedforward calculators.
 * Compensates for gravitational forces on elevators and rotating arms.
 */
object GravityFeedforward {

    /**
     * Calculates the gravity feedforward voltage/effort for a linear elevator.
     * @param kG The gravity compensation constant (in volts or percentage).
     * @return Gravity compensation feedforward value.
     */
    fun calculateElevator(kG: Double): Double {
        require(kG.isFinite()) { "kG must be finite" }
        return kG
    }

    /**
     * Calculates the gravity feedforward voltage/effort for a rotational arm.
     * @param angleRadians The current angle of the arm relative to horizontal (0 rad = horizontal).
     * @param kG The maximum gravity compensation constant at the horizontal position.
     * @return Gravity compensation feedforward value.
     */
    fun calculateArm(angleRadians: Double, kG: Double): Double {
        require(angleRadians.isFinite()) { "angleRadians must be finite" }
        require(kG.isFinite()) { "kG must be finite" }
        return kG * cos(angleRadians)
    }
}
