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
    private val subscribers = ConcurrentHashMap<String, Any>()
    
    private var getDoubleTopicMethod: Method? = null
    private var getBooleanTopicMethod: Method? = null
    private var getStringTopicMethod: Method? = null
    private var getDoubleArrayTopicMethod: Method? = null

    private var setDoubleMethod: Method? = null
    private var setBooleanMethod: Method? = null
    private var setStringMethod: Method? = null
    private var setDoubleArrayMethod: Method? = null

    private var subscribeDoubleMethod: Method? = null
    private var subscribeBooleanMethod: Method? = null
    private var subscribeStringMethod: Method? = null

    private var getDoubleMethod: Method? = null
    private var getBooleanMethod: Method? = null
    private var getStringMethod: Method? = null

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

            val doubleTopicClass = Class.forName("edu.wpi.first.networktables.DoubleTopic")
            subscribeDoubleMethod = doubleTopicClass.getMethod("subscribe", Double::class.javaPrimitiveType, pubSubOptionArrayClass)
            val doubleSubClass = Class.forName("edu.wpi.first.networktables.DoubleSubscriber")
            getDoubleMethod = doubleSubClass.getMethod("get")

            val boolTopicClass = Class.forName("edu.wpi.first.networktables.BooleanTopic")
            subscribeBooleanMethod = boolTopicClass.getMethod("subscribe", Boolean::class.javaPrimitiveType, pubSubOptionArrayClass)
            val boolSubClass = Class.forName("edu.wpi.first.networktables.BooleanSubscriber")
            getBooleanMethod = boolSubClass.getMethod("get")

            val stringTopicClass = Class.forName("edu.wpi.first.networktables.StringTopic")
            subscribeStringMethod = stringTopicClass.getMethod("subscribe", String::class.java, pubSubOptionArrayClass)
            val stringSubClass = Class.forName("edu.wpi.first.networktables.StringSubscriber")
            getStringMethod = stringSubClass.getMethod("get")
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

    fun getNumber(key: String, defaultValue: Double): Double {
        val instance = inst ?: return defaultValue
        try {
            val sub = subscribers.computeIfAbsent(key) {
                val topic = getDoubleTopicMethod!!.invoke(instance, key)
                subscribeDoubleMethod!!.invoke(topic, defaultValue, emptyOptions)
            }
            return getDoubleMethod!!.invoke(sub) as Double
        } catch (e: Throwable) {
            return defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val instance = inst ?: return defaultValue
        try {
            val sub = subscribers.computeIfAbsent(key) {
                val topic = getBooleanTopicMethod!!.invoke(instance, key)
                subscribeBooleanMethod!!.invoke(topic, defaultValue, emptyOptions)
            }
            return getBooleanMethod!!.invoke(sub) as Boolean
        } catch (e: Throwable) {
            return defaultValue
        }
    }

    fun getString(key: String, defaultValue: String): String {
        val instance = inst ?: return defaultValue
        try {
            val sub = subscribers.computeIfAbsent(key) {
                val topic = getStringTopicMethod!!.invoke(instance, key)
                subscribeStringMethod!!.invoke(topic, defaultValue, emptyOptions)
            }
            return getStringMethod!!.invoke(sub) as String
        } catch (e: Throwable) {
            return defaultValue
        }
    }
}
