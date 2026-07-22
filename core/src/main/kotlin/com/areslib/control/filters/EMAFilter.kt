package com.areslib.control.filters

/**
 * An Exponential Moving Average (EMA) Filter.
 * Smooths out noisy analog sensors (like distance or ultrasonic sensors) 
 * by placing exponential weight on historical data.
 * Zero-allocation filter suitable for 50Hz control loops.
 *
 * @param alpha The smoothing factor [0.0, 1.0]. 
 *              1.0 means no smoothing (current value is trusted 100%).
 *              0.0 means completely ignores new values (stuck on first value).
 */
/**
 * Class implementation for E M A Filter.
 *
 * Provides mathematical state estimation, vector filtering, or kinematic matrix operations.
 *
 * ### Physical Units & Coordinates:
 * - Position: Meters ($m$)
 * - Heading: Radians ($rad$), counter-clockwise positive
 * - Time: Seconds ($s$) or milliseconds ($ms$)
 */
class EMAFilter(private val alpha: Double) {
    private var previousEstimate: Double = 0.0
    private var hasFirstValue: Boolean = false

    init {
        require(alpha in 0.0..1.0) { "Alpha must be between 0.0 and 1.0" }
    }

    /**
     * Calculates the smoothed value based on the new input.
     * @param input The raw sensor reading.
     * @return The smoothed sensor reading.
     */
    fun calculate(input: Double): Double {
        if (!hasFirstValue) {
            previousEstimate = input
            hasFirstValue = true
            return input
        }

        val estimate = (alpha * input) + ((1.0 - alpha) * previousEstimate)
        previousEstimate = estimate
        return estimate
    }

    /**
     * Resets the filter's internal state.
     */
    fun reset() {
        hasFirstValue = false
    }
}
