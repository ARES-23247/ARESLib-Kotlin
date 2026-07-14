package com.areslib.control.filters

import com.areslib.util.RobotClock

/**
 * A debouncing filter that prevents a boolean signal from toggling too rapidly.
 * Useful for physical switches or noisy digital inputs.
 * 
 * @param risingTimeMs The time in milliseconds the signal must remain true before the output becomes true.
 * @param fallingTimeMs The time in milliseconds the signal must remain false before the output becomes false.
 */
class Debouncer(
    private val risingTimeMs: Long,
    private val fallingTimeMs: Long = risingTimeMs
) {
    private var lastStateChangeTimeMs: Long = RobotClock.currentTimeMillis()
    private var outputState: Boolean = false
    private var baselineState: Boolean = false

    /**
     * Calculates the debounced value of the input.
     * @param input The current raw input.
     * @return The debounced input.
     */
    fun calculate(input: Boolean): Boolean {
        val currentTimeMs = RobotClock.currentTimeMillis()

        if (input != baselineState) {
            baselineState = input
            lastStateChangeTimeMs = currentTimeMs
        }

        if (input) {
            if (currentTimeMs - lastStateChangeTimeMs >= risingTimeMs) {
                outputState = true
            }
        } else {
            if (currentTimeMs - lastStateChangeTimeMs >= fallingTimeMs) {
                outputState = false
            }
        }

        return outputState
    }
}
