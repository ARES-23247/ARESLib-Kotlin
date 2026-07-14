package com.areslib.math.filter

/**
 * A highly robust, allocation-minimized sliding window Median Filter.
 *
 * Extremely effective for filtering out arbitrary outlier spikes and extreme sensor noise
 * (e.g., analog distance sensors, ultrasonic rangefinders, LiDAR) without introducing the
 * mathematical phase lag/sluggishness typical of low-pass linear filters.
 *
 * @param windowSize The number of historical samples to track. Must be positive.
 */
class MedianFilter(
    private val windowSize: Int
) {
    init {
        require(windowSize > 0) { "Window size must be greater than 0" }
    }

    private val buffer = DoubleArray(windowSize)
    private val tempBuffer = DoubleArray(windowSize)
    private var size = 0
    private var writeIndex = 0

    /**
     * Updates the filter window with a new measurement and returns the current median.
     * @param measurement The raw sensor reading.
     * @return The filtered median estimate.
     */
    fun calculate(measurement: Double): Double {
        if (!measurement.isFinite()) return value

        if (size < windowSize) {
            buffer[size] = measurement
            size++
        } else {
            buffer[writeIndex] = measurement
            writeIndex = (writeIndex + 1) % windowSize
        }

        // Copy active elements to sorting buffer
        System.arraycopy(buffer, 0, tempBuffer, 0, size)
        tempBuffer.sort(0, size)

        return if (size % 2 == 1) {
            tempBuffer[size / 2]
        } else {
            (tempBuffer[size / 2 - 1] + tempBuffer[size / 2]) / 2.0
        }
    }

    /**
     * Pre-fills the entire history buffer with a specific value.
     */
    fun reset(value: Double = 0.0) {
        buffer.fill(value)
        size = windowSize
        writeIndex = 0
    }

    /**
     * Clears the filter history, forcing it to rebuild window state on subsequent inputs.
     */
    fun clear() {
        size = 0
        writeIndex = 0
    }

    /**
     * Returns the last computed median value, or 0.0 if empty.
     */
    val value: Double
        get() {
            if (size == 0) return 0.0
            System.arraycopy(buffer, 0, tempBuffer, 0, size)
            tempBuffer.sort(0, size)
            return if (size % 2 == 1) {
                tempBuffer[size / 2]
            } else {
                (tempBuffer[size / 2 - 1] + tempBuffer[size / 2]) / 2.0
            }
        }
}
