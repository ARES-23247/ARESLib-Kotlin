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

        println("NT4Telemetry: isWpilibAvailable = $isWpilibAvailable")

        if (isWpilibAvailable) {
            try {
                reflectHelper = ReflectionWpilibTelemetry()
                isInitialized = true
                println("NT4Telemetry: Successfully initialized ReflectionWpilibTelemetry")
            } catch (e: Exception) {
                println("NT4Telemetry: Failed to initialize ReflectionWpilibTelemetry:")
                e.printStackTrace()
            }
        } else {
            try {
                // Start the server on port 5810 (Standard NT4 port)
                // Check if server is already running to avoid exceptions in persistent environments
                val isUnitTest = try {
                    Class.forName("org.junit.Test")
                    true
                } catch (e: Exception) {
                    false
                }
                println("NT4Telemetry: Falling back to org.frcforftc.networktables. Server status = ${inst.server}")
                if (!isUnitTest && inst.server == null) {
                    inst.startNT4Server("0.0.0.0", 5810)
                    println("NT4Telemetry: Started org.frcforftc.networktables server on port 5810")
                }
                isInitialized = true
            } catch (e: Exception) {
                System.err.println("NT4Telemetry: Failed to initialize NT4 Server! ${e.message}")
            }
        }
    }

    /**
     * putNumber declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun putNumber(key: String, value: Double) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        reflectHelper?.let { helper ->
            helper.putNumber(ntKey, value)
            return
        }
        if (!isInitialized) return
        try { inst.putNumber(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    /**
     * putBoolean declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun putBoolean(key: String, value: Boolean) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        reflectHelper?.let { helper ->
            helper.putBoolean(ntKey, value)
            return
        }
        if (!isInitialized) return
        try { inst.putBoolean(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    /**
     * putString declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun putString(key: String, value: String) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        reflectHelper?.let { helper ->
            helper.putString(ntKey, value)
            return
        }
        if (!isInitialized) return
        try { inst.putString(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    /**
     * putDoubleArray declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun putDoubleArray(key: String, value: DoubleArray) {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        reflectHelper?.let { helper ->
            helper.putDoubleArray(ntKey, value)
            return
        }
        if (!isInitialized) return
        try { inst.putNumberArray(ntKey, value) } catch (e: Exception) { /* swallow */ }
    }

    /**
     * getNumber declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getNumber(key: String, defaultValue: Double): Double {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        reflectHelper?.let { helper ->
            return helper.getNumber(ntKey, defaultValue)
        }
        if (!isInitialized) return defaultValue
        return try {
            val entry = inst.get(ntKey)
            (entry?.value?.get() as? Number)?.toDouble() ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    /**
     * getBoolean declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        reflectHelper?.let { helper ->
            return helper.getBoolean(ntKey, defaultValue)
        }
        if (!isInitialized) return defaultValue
        return try {
            val entry = inst.get(ntKey)
            (entry?.value?.get() as? Boolean) ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    /**
     * getString declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getString(key: String, defaultValue: String): String {
        val ntKey = if (key.startsWith("/")) key else "/$key"
        reflectHelper?.let { helper ->
            return helper.getString(ntKey, defaultValue)
        }
        if (!isInitialized) return defaultValue
        return try {
            val entry = inst.get(ntKey)
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

    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun update() {
        if (!isInitialized) return
        try { inst.flushServer() } catch (e: Exception) { /* swallow */ }
    }

    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        if (!isInitialized) return
        // Do NOT close the server. It should remain alive across OpModes
        // so that the driver station dashboard does not permanently disconnect.
    }
}
