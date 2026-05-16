package com.areslib.control

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

    /**
     * Enables continuous input (e.g. for circular values like angles).
     */
    fun enableContinuousInput(minimumInput: Double, maximumInput: Double) {
        isContinuous = true
        continuousMin = minimumInput
        continuousMax = maximumInput
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
        var error = setpoint - measurement

        if (isContinuous) {
            val errorBound = (continuousMax - continuousMin) / 2.0
            error = inputModulus(error, -errorBound, errorBound)
        }

        if (dtSeconds > 0) {
            totalError += error * dtSeconds
        }
        
        val velocityError = if (dtSeconds > 0) (error - prevError) / dtSeconds else 0.0
        prevError = error

        return p * error + i * totalError + d * velocityError
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
