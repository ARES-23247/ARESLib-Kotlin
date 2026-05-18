package com.areslib.hardware.vision

import com.areslib.state.VisionMeasurement

/**
 * A thread-safe, chronological sliding window buffer that stores and sorts
 * asynchronous, out-of-order vision measurements by timestamps.
 *
 * Automatically evicts measurements older than [maxHistoryMs] relative to the latest measurement.
 */
class VisionMeasurementBuffer(val maxHistoryMs: Long = 1500L) {

    private val buffer = ArrayList<VisionMeasurement>()

    /**
     * Adds a new vision measurement to the buffer, maintaining sorted order and evicting expired entries.
     */
    fun addMeasurement(measurement: VisionMeasurement) {
        synchronized(buffer) {
            buffer.add(measurement)
            buffer.sortBy { it.timestampMs }
            evictOldEntries()
        }
    }

    /**
     * Evicts all entries older than [maxHistoryMs] relative to the newest measurement in the buffer.
     */
    private fun evictOldEntries() {
        synchronized(buffer) {
            val latest = buffer.lastOrNull() ?: return
            val cutoff = latest.timestampMs - maxHistoryMs
            buffer.removeIf { it.timestampMs < cutoff }
        }
    }

    /**
     * Retrieves all measurements recorded in the closed interval [startMs]..[endMs].
     */
    fun getMeasurementsBetween(startMs: Long, endMs: Long): List<VisionMeasurement> {
        return synchronized(buffer) {
            buffer.filter { it.timestampMs in startMs..endMs }
        }
    }

    /**
     * Deletes all entries older than [timestampMs].
     */
    fun clearBefore(timestampMs: Long) {
        synchronized(buffer) {
            buffer.removeIf { it.timestampMs < timestampMs }
        }
    }

    /**
     * Returns a copy of the buffer's contents sorted in ascending chronological order.
     */
    fun getAll(): List<VisionMeasurement> {
        return synchronized(buffer) {
            ArrayList(buffer)
        }
    }

    /**
     * Clears all measurements from the buffer.
     */
    fun clear() {
        synchronized(buffer) {
            buffer.clear()
        }
    }

    /**
     * Number of active measurements currently tracked in the buffer.
     */
    val size: Int
        get() = synchronized(buffer) { buffer.size }
}
