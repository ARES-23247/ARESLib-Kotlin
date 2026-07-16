package com.areslib.math.filter

/**
 * A highly robust, general-purpose Slew Rate Limiter utility.
 *
 * Limits the rate of change of an input signal. Extremely useful for limiting drivetrain
 * acceleration (reducing wheel slip, drivetrain mechanical shock, and current spikes)
 * or smoothing raw joystick commands from drivers.
 */
class SlewRateLimiter(
    private var positiveRateLimit: Double,
    private var negativeRateLimit: Double = -positiveRateLimit,
    initialValue: Double = 0.0
) {
    private var lastValue = initialValue
    private var hasBeenCalled = false

    /**
     * Filters the input value to enforce the rate-of-change limits.
     * @param input The target input signal.
     * @param dtSeconds The time step since the last update in seconds.
     * @return The rate-limited signal.
     */
    fun calculate(input: Double, dtSeconds: Double): Double {
        if (!hasBeenCalled) {
            lastValue = input
            hasBeenCalled = true
            return input
        }

        if (!dtSeconds.isFinite()) return lastValue
        val dt = if (dtSeconds > 0.0) dtSeconds else 0.0
        if (dt == 0.0) return lastValue

        if (!input.isFinite() || !positiveRateLimit.isFinite() || !negativeRateLimit.isFinite()) return lastValue

        val change = input - lastValue
        val posLimit = kotlin.math.abs(positiveRateLimit)
        val negLimit = -kotlin.math.abs(negativeRateLimit)
        
        val maxPositiveChange = posLimit * dt
        val maxNegativeChange = negLimit * dt

        val minChange = maxNegativeChange
        val maxChange = maxPositiveChange

        lastValue += change.coerceIn(minChange, maxChange)
        return lastValue
    }

    /**
     * Updates the rate limits dynamically.
     */
    fun setRateLimits(positiveRateLimit: Double, negativeRateLimit: Double = -positiveRateLimit) {
        this.positiveRateLimit = positiveRateLimit
        this.negativeRateLimit = negativeRateLimit
    }

    /**
     * Resets the limiter's state to a specific value.
     */
    fun reset(value: Double = 0.0) {
        lastValue = value
        hasBeenCalled = true
    }

    /**
     * Clears internal state, causing the next calculation to snap directly to the input value.
     */
    fun clear() {
        hasBeenCalled = false
        lastValue = 0.0
    }

    /**
     * Returns the last computed output value.
     */
    val value: Double
        get() = lastValue
}
