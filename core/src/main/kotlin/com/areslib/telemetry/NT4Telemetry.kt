package com.areslib.telemetry

import org.frcforftc.networktables.NetworkTablesInstance

/**
 * Pure Java NT4 telemetry implementation.
 * Streams telemetry and state data directly to ARES-Analytics.
 */
class NT4Telemetry : ITelemetry {
    private val inst = NetworkTablesInstance.getDefaultInstance()

    init {
        try {
            if (inst.server == null) {
                inst.startNT4Server("0.0.0.0", 5810)
                println("NT4Telemetry: Started NT4 server on port 5810 for ARES-Analytics")
            }
        } catch (e: Exception) {
            System.err.println("NT4Telemetry: Failed to start NT4 Server! ${e.message}")
        }
    }

    override fun putNumber(key: String, value: Double) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        try { inst.putNumber(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun putBoolean(key: String, value: Boolean) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        try { inst.putBoolean(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun putString(key: String, value: String) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        try { inst.putString(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun putDoubleArray(key: String, value: DoubleArray) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        try { inst.putNumberArray(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun getNumber(key: String, defaultValue: Double): Double {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        return try {
            val entry = inst.get(ntKey)
            (entry?.value?.get() as? Number)?.toDouble() ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        return try {
            val entry = inst.get(ntKey)
            (entry?.value?.get() as? Boolean) ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    override fun getString(key: String, defaultValue: String): String {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        return try {
            val entry = inst.get(ntKey)
            (entry?.value?.get() as? String) ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    fun putPose2d(key: String, xMeters: Double, yMeters: Double, rotationRadians: Double) {
        putDoubleArray(key, doubleArrayOf(xMeters, yMeters, rotationRadians))
    }

    override fun update() {
        try { inst.flushServer() } catch (e: Exception) { /* swallow */ }
    }

    override fun close() {
        // Keep server alive across OpModes
    }
}
