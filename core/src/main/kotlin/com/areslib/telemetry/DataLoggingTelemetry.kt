package com.areslib.telemetry

import java.util.concurrent.ConcurrentHashMap

/**
 * Composite telemetry wrapper that automatically saves all published data
 * to an offline file-based CSV log on update() while forwarding data
 * to the NT4 server if alive.
 */
class DataLoggingTelemetry(private val ntTelemetry: ITelemetry? = null) : ITelemetry {
    
    private val logger = ARESDataLogger()
    private val currentFrame = ConcurrentHashMap<String, Any>()

    override fun putNumber(key: String, value: Double) {
        currentFrame[key] = value
        ntTelemetry?.putNumber(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        currentFrame[key] = value
        ntTelemetry?.putBoolean(key, value)
    }

    override fun putString(key: String, value: String) {
        currentFrame[key] = value
        ntTelemetry?.putString(key, value)
    }

    override fun putDoubleArray(key: String, value: DoubleArray) {
        // Flatten arrays by turning them into a clean colon or pipe-separated string
        // inside CSV so we maintain the standard structure.
        currentFrame[key] = value.joinToString("|")
        ntTelemetry?.putDoubleArray(key, value)
    }

    override fun getNumber(key: String, defaultValue: Double): Double {
        return ntTelemetry?.getNumber(key, defaultValue) ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return ntTelemetry?.getBoolean(key, defaultValue) ?: defaultValue
    }

    override fun getString(key: String, defaultValue: String): String {
        return ntTelemetry?.getString(key, defaultValue) ?: defaultValue
    }

    override fun update() {
        // Inject current timestamp for chronological analysis
        currentFrame["TimestampMs"] = com.areslib.util.RobotClock.currentTimeMillis()
        
        // Log the complete frame asynchronously
        logger.logFrame(HashMap(currentFrame))
        
        // Forward the update trigger to live streaming network tables
        ntTelemetry?.update()
        
        // We do not clear the frame completely because standard telemetry backends
        // hold onto persistent states until overwritten.
    }

    /**
     * Shuts down background logging workers and standard telemetry server gracefully.
     */
    fun close() {
        logger.stop()
        if (ntTelemetry is NT4Telemetry) {
            ntTelemetry.close()
        }
    }
}
