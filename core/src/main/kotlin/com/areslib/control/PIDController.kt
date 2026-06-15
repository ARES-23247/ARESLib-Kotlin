package com.areslib.control

import com.areslib.util.RobotClock

/**
 * A pure math implementation of a PID controller, suitable for use in
 * purely functional Redux loops without depending on WPILib runtime.
 */
class PIDController(
    var p: Double,
    var i: Double,
    var d: Double
) {
    private var prevError: Double = 0.0
    private var totalError: Double = 0.0
    private var setpoint: Double = 0.0
    
    private var isContinuous: Boolean = false
    private var continuousMin: Double = 0.0
    private var continuousMax: Double = 0.0

    private var minOutput: Double = Double.NaN
    private var maxOutput: Double = Double.NaN
    private var minIntegral: Double = Double.NaN
    private var maxIntegral: Double = Double.NaN

    private var lastWarningTime: Long = 0L

    /**
     * Enables continuous input (e.g. for circular values like angles).
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
     * Sets the absolute bounds on the integral accumulator to prevent windup.
     */
    fun setIntegratorRange(min: Double, max: Double) {
        minIntegral = min
        maxIntegral = max
    }

    /**
     * Resets the accumulated error and previous error state.
     */
    fun reset() {
        prevError = 0.0
        totalError = 0.0
    }

    /**
     * Sets the target setpoint.
     */
    fun setSetpoint(setpoint: Double) {
        this.setpoint = setpoint
    }

    /**
     * Calculates the control output given the current measurement and a timestep dt.
     */
    fun calculate(measurement: Double, setpoint: Double, dtSeconds: Double): Double {
        this.setpoint = setpoint
        return calculate(measurement, dtSeconds)
    }

    /**
     * Calculates the control output given the current measurement and a timestep dt.
     */
    fun calculate(measurement: Double, dtSeconds: Double): Double {
        if (!measurement.isFinite() || !setpoint.isFinite() || dtSeconds <= 0.0) {
            val now = RobotClock.currentTimeMillis()
            if (now - lastWarningTime > 2000L) {
                System.err.println("PIDController: Invalid inputs detected (measurement=$measurement, setpoint=$setpoint, dtSeconds=$dtSeconds). Returning safe fallback 0.0.")
                lastWarningTime = now
            }
            return 0.0
        }

        var error = setpoint - measurement

        if (isContinuous) {
            val errorBound = (continuousMax - continuousMin) / 2.0
            error = inputModulus(error, -errorBound, errorBound)
        }

        totalError += error * dtSeconds
        // Anti-windup
        if (!minIntegral.isNaN()) { totalError = kotlin.math.max(totalError, minIntegral) }
        if (!maxIntegral.isNaN()) { totalError = kotlin.math.min(totalError, maxIntegral) }
        
        val velocityError = (error - prevError) / dtSeconds
        prevError = error

        var output = p * error + i * totalError + d * velocityError
        
        if (!minOutput.isNaN()) { output = kotlin.math.max(output, minOutput) }
        if (!maxOutput.isNaN()) { output = kotlin.math.min(output, maxOutput) }
        
        return output
    }
    
    private fun inputModulus(input: Double, minimumInput: Double, maximumInput: Double): Double {
        var modulus = input - minimumInput
        val wrapInput = maximumInput - minimumInput
        
        if (wrapInput <= 0) return input
        
        val numMax = (modulus / wrapInput).toInt()
        modulus -= numMax * wrapInput
        
        if (modulus < 0) {
            modulus += wrapInput
        }
        
        return modulus + minimumInput
    }
}
