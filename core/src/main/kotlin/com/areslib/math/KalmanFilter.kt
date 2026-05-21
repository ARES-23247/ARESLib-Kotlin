package com.areslib.math

/**
 * A highly optimized, single-variable (1D) Linear Kalman Filter.
 *
 * Recursively estimates the true state of a system from a series of noisy measurements.
 * Ideal for filtering continuous signals like drivetrain odometry velocity, battery voltage,
 * or gyro heading rates by balancing process model confidence (Q) against physical measurement
 * noise (R).
 *
 * @param processNoise Confidence covariance in the system process model (Q). Smaller means model is highly trusted.
 * @param measurementNoise Confidence covariance in the sensor reading (R). Smaller means sensor is highly trusted.
 */
class KalmanFilter(
    private var processNoise: Double,
    private var measurementNoise: Double,
    initialState: Double = 0.0,
    initialError: Double = 1.0
) {
    private var x = initialState // Estimated state
    private var p = initialError // Error covariance
    private var hasFirstValue = false

    /**
     * Updates the filter state with a new raw measurement and returns the optimal estimate.
     * @param measurement The raw sensor measurement.
     * @return The optimal state estimate.
     */
    fun calculate(measurement: Double): Double {
        if (!measurement.isFinite() || !processNoise.isFinite() || !measurementNoise.isFinite()) {
            return x
        }

        if (!hasFirstValue) {
            x = measurement
            p = 1.0 // Initialize with unit covariance
            hasFirstValue = true
            return measurement
        }

        // 1. Predict (Time Update)
        // x_predict = x (assuming static state model between measurements)
        p += processNoise

        // 2. Correct (Measurement Update)
        val denominator = p + measurementNoise
        val k = if (kotlin.math.abs(denominator) > 1e-12) p / denominator else 0.0 // Kalman Gain with div-by-zero protection
        
        val delta = measurement - x
        if (delta.isFinite()) {
            x += k * delta
        }
        
        p *= (1.0 - k)                     // Update error covariance

        return x
    }

    /**
     * Dynamically updates the process and measurement noise covariances.
     */
    fun setNoiseParameters(processNoise: Double, measurementNoise: Double) {
        this.processNoise = processNoise
        this.measurementNoise = measurementNoise
    }

    /**
     * Resets the filter to a specific state and covariance.
     */
    fun reset(state: Double = 0.0, error: Double = 1.0) {
        x = state
        p = error
        hasFirstValue = true
    }

    /**
     * Clears internal history, forcing the next measurement to initialize state directly.
     */
    fun clear() {
        x = 0.0
        p = 1.0
        hasFirstValue = false
    }

    /**
     * Returns the last computed optimal estimate.
     */
    val value: Double
        get() = x
}
