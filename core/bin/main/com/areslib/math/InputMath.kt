package com.areslib.math

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.pow

object InputMath {
    /**
     * Applies a symmetric deadband to the input value.
     */
    fun applyDeadband(value: Double, deadband: Double): Double {
        if (abs(value) < deadband) return 0.0
        return (value - sign(value) * deadband) / (1.0 - deadband)
    }

    /**
     * Applies an exponential curve to the input value while maintaining its sign.
     */
    fun applyCurve(value: Double, exponent: Double = 2.0): Double {
        return sign(value) * abs(value).pow(exponent)
    }
}
