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
        if (!kG.isFinite()) return 0.0
        return kG
    }

    /**
     * Calculates the gravity feedforward voltage/effort for a linear elevator scaled by payload.
     * @param baseKG The base gravity compensation constant (when empty).
     * @param inventoryCount The number of loaded game pieces.
     * @param factorPerPiece The scaling factor added per loaded piece (e.g. 0.1 for 10% per piece).
     * @return Gravity compensation feedforward value.
     */
    fun calculateAdaptiveElevator(baseKG: Double, inventoryCount: Int, factorPerPiece: Double = 0.1): Double {
        if (!baseKG.isFinite() || !factorPerPiece.isFinite()) return 0.0
        val count = if (inventoryCount < 0) 0 else inventoryCount
        return baseKG * (1.0 + factorPerPiece * count)
    }

    /**
     * Calculates the gravity feedforward voltage/effort for a rotational arm.
     * @param angleRadians The current angle of the arm relative to horizontal (0 rad = horizontal).
     * @param kG The maximum gravity compensation constant at the horizontal position.
     * @return Gravity compensation feedforward value.
     */
    fun calculateArm(angleRadians: Double, kG: Double): Double {
        if (!angleRadians.isFinite() || !kG.isFinite()) return 0.0
        return kG * cos(angleRadians)
    }
}
