package com.areslib.control.filters

import com.areslib.util.RobotClock
import kotlin.math.abs
import kotlin.math.sign

/**
 * A slew rate limiter that limits the rate of change of a signal over time.
 * Excellent for preventing sudden joystick jerks from browning out the robot battery,
 * or for smoothing out stepped velocity setpoints.
 * Zero-allocation filter suitable for 50Hz control loops.
 *
 * @param rateLimit The maximum allowed rate of change in units per second.
 * @param initialValue The initial state of the filter.
 */
class SlewRateLimiter(
    private var rateLimit: Double,
    initialValue: Double = 0.0
) {
    private var previousValue: Double = initialValue
    private var previousTimeMs: Long = RobotClock.currentTimeMillis()

    /**
     * Calculates the rate-limited output.
     * @param input The desired target value.
     * @return The rate-limited output value.
     */
    fun calculate(input: Double): Double {
        val currentTimeMs = RobotClock.currentTimeMillis()
        val elapsedTimeSec = (currentTimeMs - previousTimeMs) / 1000.0
        
        // Handle edge case where calculate is called extremely fast or clock is identical
        if (elapsedTimeSec <= 0.0) {
            return previousValue
        }

        previousTimeMs = currentTimeMs

        val diff = input - previousValue
        val maxChange = rateLimit * elapsedTimeSec
        
        previousValue += if (abs(diff) > maxChange) {
            maxChange * sign(diff)
        } else {
            diff
        }

        return previousValue
    }

    /**
     * Resets the filter to a specific value.
     */
    fun reset(value: Double) {
        previousValue = value
        previousTimeMs = RobotClock.currentTimeMillis()
    }
}
