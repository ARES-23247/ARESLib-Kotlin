package com.areslib.math

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.pow

object InputMath {
    /**
     * Applies a symmetric deadband to the input value.
     */
    fun applyDeadband(value: Double, deadband: Double): Double {
        val denominator = 1.0 - deadband
        return when {
            abs(value) < deadband -> 0.0
            abs(denominator) < 1e-6 -> 0.0 // Guard against division by zero
            else -> (value - sign(value) * deadband) / denominator
        }
    }

    /**
     * Applies an exponential curve to the input value while maintaining its sign.
     */
    fun applyCurve(value: Double, exponent: Double = 2.0): Double {
        return sign(value) * abs(value).pow(exponent)
    }

}

