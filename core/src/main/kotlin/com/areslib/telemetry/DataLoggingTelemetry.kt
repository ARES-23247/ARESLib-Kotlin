package com.areslib.telemetry

import java.util.concurrent.ConcurrentHashMap

/**
 * Composite telemetry wrapper that automatically saves all published data
 * to an offline file-based CSV log on update() while forwarding data
 * to the NT4 server if alive.
 */
class DataLoggingTelemetry(private val ntTelemetry: ITelemetry? = null) : ITelemetry {
    
    private var logger = ARESDataLogger("Init")
    private val currentFrame = ConcurrentHashMap<String, Any>()
    private var currentMode = "Init"

    init {
        ntTelemetry?.putString("OpMode", currentMode)
    }

    /**
     * The minimum time interval in milliseconds between file-based logging writes.
     * Defaults to 20ms (maximum 50Hz logging rate) to prevent storage and CPU congestion.
     */
    var minLogIntervalMs: Long = 20L
    
    private var lastLogTimeMs = 0L

    override fun putNumber(key: String, value: Double) {
        currentFrame[key] = value
        ntTelemetry?.putNumber(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        currentFrame[key] = if (value) 1.0 else 0.0
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
        val now = com.areslib.util.RobotClock.currentTimeMillis()

        // Check if mode transitioned
        val detectedMode = com.areslib.telemetry.RobotStatusTracker.activeOpMode
        if (detectedMode != currentMode) {
            logger.stop()
            currentMode = detectedMode
            logger = ARESDataLogger(currentMode)
            ntTelemetry?.putString("OpMode", currentMode)
        }
        
        // Log the complete frame asynchronously using the GC-free map pool only if interval elapsed
        if (now - lastLogTimeMs >= minLogIntervalMs) {
            lastLogTimeMs = now
            currentFrame["TimestampMs"] = now
            currentFrame["OpMode"] = currentMode
            
            val map = logger.obtainMap()
            map.putAll(currentFrame)
            logger.logFrame(map)
        }
        
        // Forward the update trigger to live streaming network tables (always unthrottled)
        ntTelemetry?.update()
        
        // We do not clear the frame completely because standard telemetry backends
        // hold onto persistent states until overwritten.
    }

    /**
     * Shuts down background logging workers and standard telemetry server gracefully.
     */
    override fun close() {
        logger.stop()
        ntTelemetry?.close()
    }
}
