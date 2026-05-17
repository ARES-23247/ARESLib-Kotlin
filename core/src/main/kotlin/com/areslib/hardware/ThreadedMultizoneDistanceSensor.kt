package com.areslib.hardware

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Thread-safe wrapper that polls a slow MultizoneDistanceSensorIO (like the VL53L5CX) on a background thread
 * at a fixed rate, completely eliminating heavy synchronous I2C blocking delays from the main control loop.
 */
class ThreadedMultizoneDistanceSensor(
    private val physicalSensor: MultizoneDistanceSensorIO,
    pollIntervalMs: Long = 33 // ~30 Hz poll rate (ideal for VL53L5CX native framerates)
) : MultizoneDistanceSensorIO {

    override val rows: Int get() = physicalSensor.rows
    override val columns: Int get() = physicalSensor.columns

    @Volatile
    private var cachedDistances: DoubleArray = DoubleArray(rows * columns)

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { thread ->
        Thread(thread, "ARES-Multizone-ToF-Polling-Thread").apply { isDaemon = true }
    }

    init {
        scheduler.scheduleAtFixedRate({
            try {
                cachedDistances = physicalSensor.distancesMeters
            } catch (e: Exception) {
                // Keep last cached values
            }
        }, 0, pollIntervalMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Instantly returns the latest cached zone readings from memory (0.0 ms execution time).
     */
    override val distancesMeters: DoubleArray
        get() = cachedDistances

    /**
     * Safely shuts down the polling background thread.
     */
    fun shutdown() {
        scheduler.shutdown()
    }
}
