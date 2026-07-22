package com.areslib.control.feedback

import com.areslib.util.RobotClock

/**
 * Pure mathematical Proportional-Integral-Derivative (PID) feedback controller with anti-windup and continuous input wrapping.
 *
 * Designed for high-frequency (50Hz–1000Hz) closed-loop control in zero-GC Redux architecture pipelines.
 *
 * ### Mathematical Formulation:
 * Continuous-time error dynamics:
 * $$e(t) = r(t) - y(t)$$
 * Control law with discrete integration and derivative calculation:
 * $$u(k) = K_p \cdot e(k) + K_i \sum_{i=0}^{k} e(i) \Delta t + K_d \frac{e(k) - e(k-1)}{\Delta t}$$
 *
 * ### Anti-Windup & Output Clamping:
 * Integral accumulator is bounded between $[I_{min}, I_{max}]$. Final effort output $u(k)$ is clamped to $[u_{min}, u_{max}]$.
 *
 * @param p Proportional gain coefficient $K_p$.
 * @param i Integral gain coefficient $K_i$.
 * @param d Derivative gain coefficient $K_d$.
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

    /** Error deadzone threshold below which control effort is forced to 0.0. */
    var deadzone: Double = 0.0

    private var lastWarningTime: Long = 0L

    /**
     * Enables continuous input domain wrapping (e.g., $[-\pi, \pi]$ or $[0, 360^\circ]$) to compute the shortest error path.
     *
     * @param minimumInput Lower bound of continuous domain.
     * @param maximumInput Upper bound of continuous domain.
     */
    fun enableContinuousInput(minimumInput: Double, maximumInput: Double) {
        isContinuous = true
        continuousMin = minimumInput
        continuousMax = maximumInput
        reset()
    }

    /**
     * Sets the minimum and maximum output effort clamping bounds $[u_{min}, u_{max}]$.
     *
     * @param min Minimum allowable control output.
     * @param max Maximum allowable control output.
     */
    fun setOutputLimits(min: Double, max: Double) {
        minOutput = min
        maxOutput = max
    }

    /**
     * Configures absolute anti-windup bounds $[I_{min}, I_{max}]$ on the accumulated error sum.
     *
     * @param min Minimum allowable integral sum.
     * @param max Maximum allowable integral sum.
     */
    fun setIntegratorRange(min: Double, max: Double) {
        minIntegral = min
        maxIntegral = max
    }

    /**
     * Resets the accumulated integral error and previous error state to zero.
     */
    fun reset() {
        prevError = 0.0
        totalError = 0.0
    }

    /**
     * Sets the target setpoint $r(t)$.
     *
     * @param setpoint Desired target state value.
     */
    fun setSetpoint(setpoint: Double) {
        this.setpoint = setpoint
    }

    /**
     * Calculates control effort given current measurement, target setpoint, and loop timestep $\Delta t$.
     *
     * @param measurement Measured process variable $y(k)$.
     * @param setpoint Desired target setpoint $r(k)$.
     * @param dtSeconds Timestep duration in seconds ($\Delta t > 0$).
     * @return Computed control effort $u(k)$.
     */
    fun calculate(measurement: Double, setpoint: Double, dtSeconds: Double): Double {
        this.setpoint = setpoint
        return calculate(measurement, dtSeconds)
    }

    /**
     * Calculates control effort using the pre-configured target setpoint and current measurement.
     *
     * @param measurement Measured process variable $y(k)$.
     * @param dtSeconds Timestep duration in seconds ($\Delta t > 0$).
     * @return Computed control effort $u(k)$.
     */
    fun calculate(measurement: Double, dtSeconds: Double): Double {
        if (!measurement.isFinite() || !setpoint.isFinite() || !dtSeconds.isFinite() || dtSeconds <= 0.0) {
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

        if (kotlin.math.abs(error) < deadzone) {
            prevError = error
            return 0.0
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
