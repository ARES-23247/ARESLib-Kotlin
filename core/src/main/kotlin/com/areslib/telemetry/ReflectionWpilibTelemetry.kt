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

    private var setDoubleMethod: Method? = null
    private var setBooleanMethod: Method? = null
    private var setStringMethod: Method? = null
    private var setDoubleArrayMethod: Method? = null

    private var pubSubOptionArrayClass: Class<*>? = null
    private var emptyOptions: Any? = null

    init {
        try {
            val instClass = Class.forName("edu.wpi.first.networktables.NetworkTableInstance")
            val getDefaultMethod = instClass.getMethod("getDefault")
            inst = getDefaultMethod.invoke(null)
            
            getDoubleTopicMethod = instClass.getMethod("getDoubleTopic", String::class.java)
            getBooleanTopicMethod = instClass.getMethod("getBooleanTopic", String::class.java)
            getStringTopicMethod = instClass.getMethod("getStringTopic", String::class.java)
            getDoubleArrayTopicMethod = instClass.getMethod("getDoubleArrayTopic", String::class.java)

            val pubSubOptionClass = Class.forName("edu.wpi.first.networktables.PubSubOption")
            pubSubOptionArrayClass = java.lang.reflect.Array.newInstance(pubSubOptionClass, 0).javaClass
            emptyOptions = java.lang.reflect.Array.newInstance(pubSubOptionClass, 0)

            val doublePubClass = Class.forName("edu.wpi.first.networktables.DoublePublisher")
            setDoubleMethod = doublePubClass.getMethod("set", Double::class.javaPrimitiveType)

            val boolPubClass = Class.forName("edu.wpi.first.networktables.BooleanPublisher")
            setBooleanMethod = boolPubClass.getMethod("set", Boolean::class.javaPrimitiveType)

            val stringPubClass = Class.forName("edu.wpi.first.networktables.StringPublisher")
            setStringMethod = stringPubClass.getMethod("set", String::class.java)

            val doubleArrayPubClass = Class.forName("edu.wpi.first.networktables.DoubleArrayPublisher")
            setDoubleArrayMethod = doubleArrayPubClass.getMethod("set", DoubleArray::class.java)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: Failed to initialize reflection bindings: ${e.message}")
            e.printStackTrace()
        }
    }

    fun putNumber(key: String, value: Double) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getDoubleTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish", pubSubOptionArrayClass)
                publishMethod.invoke(topic, emptyOptions)
            }
            setDoubleMethod!!.invoke(pub, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting number for $key: ${e.message}")
            e.printStackTrace()
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getBooleanTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish", pubSubOptionArrayClass)
                publishMethod.invoke(topic, emptyOptions)
            }
            setBooleanMethod!!.invoke(pub, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting boolean for $key: ${e.message}")
            e.printStackTrace()
        }
    }

    fun putString(key: String, value: String) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getStringTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish", pubSubOptionArrayClass)
                publishMethod.invoke(topic, emptyOptions)
            }
            setStringMethod!!.invoke(pub, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting string for $key: ${e.message}")
            e.printStackTrace()
        }
    }

    fun putDoubleArray(key: String, value: DoubleArray) {
        val instance = inst ?: return
        try {
            val pub = publishers.computeIfAbsent(key) {
                val topic = getDoubleArrayTopicMethod!!.invoke(instance, key)
                val publishMethod = topic.javaClass.getMethod("publish", pubSubOptionArrayClass)
                publishMethod.invoke(topic, emptyOptions)
            }
            setDoubleArrayMethod!!.invoke(pub, value)
        } catch (e: Throwable) {
            System.err.println("ReflectionWpilibTelemetry: error putting double array for $key: ${e.message}")
            e.printStackTrace()
        }
    }
}
