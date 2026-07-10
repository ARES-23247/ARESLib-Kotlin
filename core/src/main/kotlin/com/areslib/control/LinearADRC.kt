package com.areslib.control

import kotlin.math.abs

/**
 * A Linear Active Disturbance Rejection Controller (LADRC) for a first-order system.
 *
 * This controller uses an Extended State Observer (ESO) to estimate and cancel out
 * total disturbances (unmodeled dynamics + external forces) in real-time. It is highly
 * robust to physical collisions, changing battery voltage, and friction.
 *
 * @param b0 The estimated control gain (rough approximation of system responsiveness, e.g., max speed / max voltage).
 * @param omegaC The controller bandwidth (rad/s). Determines how aggressively the system tracks the reference.
 * @param omegaO The observer bandwidth (rad/s). Determines how fast the ESO estimates disturbances. Generally 3x to 5x larger than omegaC.
 */
class LinearADRC(
    var b0: Double,
    var omegaC: Double,
    var omegaO: Double
) {
    // Observer states
    var xHat1: Double = 0.0 // Estimated state (position/velocity)
    var xHat2: Double = 0.0 // Estimated disturbance

    // Previous control output for observer prediction
    private var uPrev: Double = 0.0

    // Output limits
    private var minOutput: Double = Double.NaN
    private var maxOutput: Double = Double.NaN

    // Continuous input handling (e.g., heading wrapping)
    private var isContinuous: Boolean = false
    private var continuousMin: Double = 0.0
    private var continuousMax: Double = 0.0

    /**
     * Enables continuous input (e.g. for circular values like angles).
     * The shortest path will be taken to the target.
     */
    fun enableContinuousInput(minimumInput: Double, maximumInput: Double) {
        isContinuous = true
        continuousMin = minimumInput
        continuousMax = maximumInput
    }

    /**
     * Sets the minimum and maximum output clamping limits.
     */
    fun setOutputLimits(min: Double, max: Double) {
        minOutput = min
        maxOutput = max
    }

    /**
     * Resets the observer states to match a current measurement.
     * Prevents violent jumps when turning the controller on for the first time.
     */
    fun reset(measurement: Double) {
        xHat1 = measurement
        xHat2 = 0.0
        uPrev = 0.0
    }

    /**
     * Calculates the control output based on the target, current measurement, and time step.
     *
     * @param target Desired setpoint.
     * @param measurement Current measured value.
     * @param dtSeconds Time elapsed since the last call (seconds).
     * @return The commanded control effort (e.g., motor voltage).
     */
    fun calculate(target: Double, measurement: Double, dtSeconds: Double): Double {
        if (dtSeconds <= 0.0) return 0.0

        var actualTarget = target
        var actualMeasurement = measurement

        // Handle continuous input wrapping
        if (isContinuous) {
            val range = continuousMax - continuousMin
            var errorBound = (actualTarget - actualMeasurement) % range
            
            if (abs(errorBound) > (range / 2.0)) {
                if (errorBound > 0.0) {
                    errorBound -= range
                } else {
                    errorBound += range
                }
            }
            // For the observer, we manipulate the measurement so that the error
            // term represents the shortest path. We anchor the measurement.
            actualTarget = actualMeasurement + errorBound
        }

        // 1. Update the Extended State Observer (ESO)
        // Observer gains based on bandwidth parametrization
        val l1 = 2.0 * omegaO
        val l2 = omegaO * omegaO

        val observerError = actualMeasurement - xHat1

        // Euler integration for observer states
        xHat1 += (xHat2 + b0 * uPrev + l1 * observerError) * dtSeconds
        xHat2 += (l2 * observerError) * dtSeconds

        // 2. Control Law
        // PD-like control effort on the error between target and estimated state
        val kp = omegaC
        val u0 = kp * (actualTarget - xHat1)

        // Cancel out the disturbance (xHat2) and scale by the system gain
        var u = (u0 - xHat2) / b0

        // Output Clamping
        u = when {
            !minOutput.isNaN() && u < minOutput -> minOutput
            !maxOutput.isNaN() && u > maxOutput -> maxOutput
            else -> u
        }

        uPrev = u
        return u
    }
}
