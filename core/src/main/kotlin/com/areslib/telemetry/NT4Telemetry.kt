package com.areslib.telemetry

import org.frcforftc.networktables.NetworkTablesInstance

/**
 * An implementation of ITelemetry using the pure Java NT4 server.
 * Streams data to an external FRC dashboard like AdvantageScope.
 */
class NT4Telemetry : ITelemetry {
    private val inst = NetworkTablesInstance.getDefaultInstance()

    init {
        // Start the server on port 5810 (Standard NT4 port)
        // Check if server is already running to avoid exceptions in persistent environments
        if (inst.server == null) {
            inst.startNT4Server("0.0.0.0", 5810)
        }
    }

    override fun putNumber(key: String, value: Double) {
        inst.putNumber(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        inst.putBoolean(key, value)
    }

    override fun putString(key: String, value: String) {
        inst.putString(key, value)
    }

    override fun putDoubleArray(key: String, value: DoubleArray) {
        inst.putNumberArray(key, value)
    }

    override fun getNumber(key: String, defaultValue: Double): Double {
        val entry = inst.get(key)
        return (entry?.value?.get() as? Number)?.toDouble() ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val entry = inst.get(key)
        return (entry?.value?.get() as? Boolean) ?: defaultValue
    }

    override fun getString(key: String, defaultValue: String): String {
        val entry = inst.get(key)
        return (entry?.value?.get() as? String) ?: defaultValue
    }

    /**
     * Helper specifically for Pose2d to format correctly for AdvantageScope.
     * AdvantageScope expects [x, y, rotationRadians] format for Pose2d topics when represented as double[].
     */
    fun putPose2d(key: String, xMeters: Double, yMeters: Double, rotationRadians: Double) {
        putDoubleArray(key, doubleArrayOf(xMeters, yMeters, rotationRadians))
    }
    
    fun close() {
        inst.closeServer()
    }
}
