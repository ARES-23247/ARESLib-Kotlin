package com.areslib.telemetry

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * A reflection-based helper that interacts with official WPILib NetworkTableInstance
 * when running on Desktop JVM (in the simulator). This avoids compile-time dependencies
 * in the core module while preventing classloading issues on Android (where WPILib is not available).
 */
class ReflectionWpilibTelemetry {
    private var inst: Any? = null
    private val entries = ConcurrentHashMap<String, Any>()
    
    private var getEntryMethod: Method? = null
    private var setDoubleMethod: Method? = null
    private var setBooleanMethod: Method? = null
    private var setStringMethod: Method? = null
    private var setDoubleArrayMethod: Method? = null
    
    private var getDoubleMethod: Method? = null
    private var getBooleanMethod: Method? = null
    private var getStringMethod: Method? = null

    init {
        try {
            val instClass = Class.forName("edu.wpi.first.networktables.NetworkTableInstance")
            val getDefaultMethod = instClass.getMethod("getDefault")
            inst = getDefaultMethod.invoke(null)
            
            getEntryMethod = instClass.getMethod("getEntry", String::class.java)
            
            val entryClass = Class.forName("edu.wpi.first.networktables.NetworkTableEntry")
            setDoubleMethod = entryClass.getMethod("setDouble", Double::class.javaPrimitiveType)
            setBooleanMethod = entryClass.getMethod("setBoolean", Boolean::class.javaPrimitiveType)
            setStringMethod = entryClass.getMethod("setString", String::class.java)
            setDoubleArrayMethod = entryClass.getMethod("setDoubleArray", DoubleArray::class.java)
            
            getDoubleMethod = entryClass.getMethod("getDouble", Double::class.javaPrimitiveType)
            getBooleanMethod = entryClass.getMethod("getBoolean", Boolean::class.javaPrimitiveType)
            getStringMethod = entryClass.getMethod("getString", String::class.java)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: Failed to initialize reflection bindings: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getEntry(key: String): Any? {
        val instance = inst ?: return null
        return entries.computeIfAbsent(key) {
            try {
                getEntryMethod!!.invoke(instance, key)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * putNumber declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun putNumber(key: String, value: Double) {
        val entry = getEntry(key) ?: return
        try {
            setDoubleMethod!!.invoke(entry, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting number for $key: ${e.message}")
        }
    }

    /**
     * putBoolean declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun putBoolean(key: String, value: Boolean) {
        val entry = getEntry(key) ?: return
        try {
            setBooleanMethod!!.invoke(entry, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting boolean for $key: ${e.message}")
        }
    }

    /**
     * putString declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun putString(key: String, value: String) {
        val entry = getEntry(key) ?: return
        try {
            setStringMethod!!.invoke(entry, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting string for $key: ${e.message}")
        }
    }

    /**
     * putDoubleArray declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun putDoubleArray(key: String, value: DoubleArray) {
        val entry = getEntry(key) ?: return
        try {
            setDoubleArrayMethod!!.invoke(entry, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting double array for $key: ${e.message}")
        }
    }

    /**
     * getNumber declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getNumber(key: String, defaultValue: Double): Double {
        val entry = getEntry(key) ?: return defaultValue
        return try {
            getDoubleMethod!!.invoke(entry, defaultValue) as Double
        } catch (e: Throwable) {
            defaultValue
        }
    }

    /**
     * getBoolean declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val entry = getEntry(key) ?: return defaultValue
        return try {
            getBooleanMethod!!.invoke(entry, defaultValue) as Boolean
        } catch (e: Throwable) {
            defaultValue
        }
    }

    /**
     * getString declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getString(key: String, defaultValue: String): String {
        val entry = getEntry(key) ?: return defaultValue
        return try {
            getStringMethod!!.invoke(entry, defaultValue) as String
        } catch (e: Throwable) {
            defaultValue
        }
    }
}
