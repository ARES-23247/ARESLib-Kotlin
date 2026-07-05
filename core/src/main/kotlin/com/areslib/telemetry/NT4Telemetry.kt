package com.areslib.telemetry

import org.frcforftc.networktables.NetworkTablesInstance

/**
 * An implementation of ITelemetry using the pure Java NT4 server.
 * Streams data to an external FRC dashboard like AdvantageScope.
 */
class NT4Telemetry : ITelemetry {
    private val inst = NetworkTablesInstance.getDefaultInstance()
    private var isInitialized = false
    private var reflectHelper: ReflectionWpilibTelemetry? = null

    init {
        val isWpilibAvailable = try {
            Class.forName("edu.wpi.first.networktables.NetworkTableInstance")
            true
        } catch (e: Exception) {
            false
        }

        if (isWpilibAvailable) {
            try {
                reflectHelper = ReflectionWpilibTelemetry()
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                // Start the server on port 5810 (Standard NT4 port)
                // Check if server is already running to avoid exceptions in persistent environments
                if (inst.server == null) {
                    inst.startNT4Server("0.0.0.0", 5810)
                }
                isInitialized = true
            } catch (e: Exception) {
                System.err.println("NT4Telemetry: Failed to initialize NT4 Server! ${e.message}")
            }
        }
    }

    override fun putNumber(key: String, value: Double) {
        reflectHelper?.let { helper ->
            helper.putNumber(key, value)
            return
        }
        if (!isInitialized) return
        try { inst.putNumber(key, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun putBoolean(key: String, value: Boolean) {
        reflectHelper?.let { helper ->
            helper.putBoolean(key, value)
            return
        }
        if (!isInitialized) return
        try { inst.putBoolean(key, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun putString(key: String, value: String) {
        reflectHelper?.let { helper ->
            helper.putString(key, value)
            return
        }
        if (!isInitialized) return
        try { inst.putString(key, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun putDoubleArray(key: String, value: DoubleArray) {
        reflectHelper?.let { helper ->
            helper.putDoubleArray(key, value)
            return
        }
        if (!isInitialized) return
        try { inst.putNumberArray(key, value) } catch (e: Exception) { /* swallow */ }
    }

    override fun getNumber(key: String, defaultValue: Double): Double {
        if (!isInitialized) return defaultValue
        return try {
            val entry = inst.get(key)
            (entry?.value?.get() as? Number)?.toDouble() ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        if (!isInitialized) return defaultValue
        return try {
            val entry = inst.get(key)
            (entry?.value?.get() as? Boolean) ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    override fun getString(key: String, defaultValue: String): String {
        if (!isInitialized) return defaultValue
        return try {
            val entry = inst.get(key)
            (entry?.value?.get() as? String) ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    /**
     * Helper specifically for Pose2d to format correctly for AdvantageScope.
     * AdvantageScope expects [x, y, rotationRadians] format for Pose2d topics when represented as double[].
     */
    fun putPose2d(key: String, xMeters: Double, yMeters: Double, rotationRadians: Double) {
        putDoubleArray(key, doubleArrayOf(xMeters, yMeters, rotationRadians))
    }
}
