package com.areslib.math

/**
 * A highly robust, single-pole IIR Low-Pass Filter (exponential moving average).
 *
 * Smooths noisy inputs (e.g., analog distance sensors, current measurements, battery voltage)
 * using a configurable time constant (RC) to ensure consistent physical timing independent
 * of varying loop frequencies (dt).
 */
class LowPassFilter(
    private var timeConstantSeconds: Double
) {
    private var lastEstimate = 0.0
    private var hasFirstValue = false

    /**
     * Updates the filter with a new raw input measurement.
     * @param measurement The noisy raw input value.
     * @param dtSeconds The time elapsed since the last update in seconds.
     * @return The smoothed/filtered estimate.
     */
    fun calculate(measurement: Double, dtSeconds: Double): Double {
        if (!hasFirstValue) {
            lastEstimate = measurement
            hasFirstValue = true
            return measurement
        }

        // If time constant is tiny or negative, bypass filter
        if (timeConstantSeconds <= 0.0) {
            lastEstimate = measurement
            return measurement
        }

        // Safeguard dtSeconds from zero or negative time jumps
        val dt = if (dtSeconds > 0.0) dtSeconds else 0.0
        
        // Alpha = dt / (RC + dt)
        val alpha = dt / (timeConstantSeconds + dt)
        lastEstimate = alpha * measurement + (1.0 - alpha) * lastEstimate
        return lastEstimate
    }

    /**
     * Manually overrides the time constant of the filter.
     */
    fun setTimeConstant(timeConstantSeconds: Double) {
        this.timeConstantSeconds = timeConstantSeconds
    }

    /**
     * Resets the filter's internal state to a specific starting value.
     */
    fun reset(value: Double = 0.0) {
        lastEstimate = value
        hasFirstValue = true
    }

    /**
     * Clear the filter history, forcing the next measurement to initialize the state directly.
     */
    fun clear() {
        hasFirstValue = false
        lastEstimate = 0.0
    }

    /**
     * Returns the last computed estimate.
     */
    val value: Double
        get() = lastEstimate
}
