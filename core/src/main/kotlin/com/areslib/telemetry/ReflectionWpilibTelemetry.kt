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
    private val publishers = ConcurrentHashMap<String, Any>()
    
    private var getDoubleTopicMethod: Method? = null
    private var getBooleanTopicMethod: Method? = null
    private var getStringTopicMethod: Method? = null
    private var getDoubleArrayTopicMethod: Method? = null

    init {
        try {
            val instClass = Class.forName("edu.wpi.first.networktables.NetworkTableInstance")
            val getDefaultMethod = instClass.getMethod("getDefault")
            inst = getDefaultMethod.invoke(null)
            
            getDoubleTopicMethod = instClass.getMethod("getDoubleTopic", String::class.java)
            getBooleanTopicMethod = instClass.getMethod("getBooleanTopic", String::class.java)
            getStringTopicMethod = instClass.getMethod("getStringTopic", String::class.java)
            getDoubleArrayTopicMethod = instClass.getMethod("getDoubleArrayTopic", String::class.java)
        } catch (e: Exception) {
            System.err.println("ReflectionWpilibTelemetry: Failed to initialize reflection bindings: ${e.message}")
        }
    }

    fun putNumber(key: String, value: Double) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getDoubleTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish")
                publishMethod.invoke(topic)
            }
            val setMethod = pub.javaClass.getMethod("set", Double::class.javaPrimitiveType)
            setMethod.invoke(pub, value)
        } catch (e: Exception) {
            // Ignore reflection errors during hot loop
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getBooleanTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish")
                publishMethod.invoke(topic)
            }
            val setMethod = pub.javaClass.getMethod("set", Boolean::class.javaPrimitiveType)
            setMethod.invoke(pub, value)
        } catch (e: Exception) {
            // Ignore reflection errors during hot loop
        }
    }

    fun putString(key: String, value: String) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getStringTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish")
                publishMethod.invoke(topic)
            }
            val setMethod = pub.javaClass.getMethod("set", String::class.java)
            setMethod.invoke(pub, value)
        } catch (e: Exception) {
            // Ignore reflection errors during hot loop
        }
    }

    fun putDoubleArray(key: String, value: DoubleArray) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getDoubleArrayTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish")
                publishMethod.invoke(topic)
            }
            val setMethod = pub.javaClass.getMethod("set", DoubleArray::class.java)
            setMethod.invoke(pub, value)
        } catch (e: Exception) {
            // Ignore reflection errors during hot loop
        }
    }
}
