package com.areslib.telemetry

/**
 * Platform-agnostic telemetry interface used by the functional core to publish
 * variables, tuning parameters, and robot state.
 */
interface ITelemetry {
    fun putNumber(key: String, value: Double)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
    fun putDoubleArray(key: String, value: DoubleArray)
    
    fun getNumber(key: String, defaultValue: Double): Double
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String, defaultValue: String): String
    
    /**
     * Optional method to process periodic updates.
     */
    fun update() {}
}
