package com.areslib.networktables

import java.util.concurrent.CopyOnWriteArrayList

enum class NT4EventType {
    TOPIC_PUBLISHED,
    TOPIC_UNPUBLISHED,
    TOPIC_UPDATED
}

fun interface NT4EventListener {
    fun onEvent(entry: NT4Entry, eventType: NT4EventType, value: NT4Value)
}

/**
 * Represents an active NetworkTables entry in ARESLib-Kotlin.
 * Thread-safe with listener dispatch support.
 */
class NT4Entry(
    var id: Int,
    val topic: String,
    @Volatile var value: NT4Value
) {
    private val listeners = CopyOnWriteArrayList<NT4EventListener>()

    fun update(newValue: NT4Value): Boolean {
        if (this.value == newValue) return false
        this.value = newValue
        notifyListeners(NT4EventType.TOPIC_UPDATED, newValue)
        return true
    }

    fun addListener(listener: NT4EventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NT4EventListener) {
        listeners.remove(listener)
    }

    fun notifyListeners(eventType: NT4EventType, value: NT4Value) {
        for (listener in listeners) {
            try {
                listener.onEvent(this, eventType, value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
