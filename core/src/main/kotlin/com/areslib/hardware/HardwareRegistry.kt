package com.areslib.hardware

import com.areslib.telemetry.ITelemetry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global registry for automatic hardware device logging, diagnostics, power scaling, and lifecycle batch reads.
 */
object HardwareRegistry {
    private val devices = ConcurrentHashMap<String, LoggableDevice>()
    private val closeables = CopyOnWriteArrayList<AutoCloseable>()

    /**
     * Registers a closeable hardware wrapper to ensure background threads are terminated on close.
     */
    fun registerCloseable(closeable: AutoCloseable) {
        closeables.add(closeable)
    }

    /**
     * Registers a generic loggable device with a unique system name.
     */
    fun registerDevice(name: String, device: LoggableDevice) {
        devices[name] = device
    }

    /**
     * Registers a motor with a unique diagnostic name.
     */
    fun registerMotor(name: String, motor: MotorIO) {
        registerDevice("Motors/$name", motor)
    }

    /**
     * Registers a servo with a unique diagnostic name.
     */
    fun registerServo(name: String, servo: ServoIO) {
        registerDevice("Servos/$name", servo)
    }

    /**
     * Retrieves all registered motor wrappers for power budget calculations and voltage compensation.
     */
    fun getRegisteredMotors(): List<MotorIO> {
        return devices.values.filterIsInstance<MotorIO>()
    }

    /**
     * Retrieves all registered motor wrappers mapped by their registered names.
     */
    fun getRegisteredMotorsWithNames(): Map<String, MotorIO> {
        val result = HashMap<String, MotorIO>()
        for ((key, value) in devices) {
            if (value is MotorIO) {
                val name = if (key.startsWith("Motors/")) key.substring("Motors/".length) else key
                result[name] = value
            }
        }
        return result
    }

    /**
     * Batches status updates and read transactions across all registered SubsystemIO components.
     */
    fun refreshAll() {
        for (device in devices.values) {
            if (device is SubsystemIO) {
                device.refresh()
            }
        }
    }

    /**
     * Triggers safety failsafes across all registered subsystems.
     */
    fun safeAll() {
        for (device in devices.values) {
            if (device is SubsystemIO) {
                try {
                    device.safe()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Clears all registered devices and terminates background threads.
     */
    fun closeAll() {
        for (c in closeables) {
            try {
                c.close()
            } catch (_: Exception) {}
        }
        closeables.clear()

        for (device in devices.values) {
            if (device is AutoCloseable) {
                try {
                    device.close()
                } catch (_: Exception) {}
            }
        }
        devices.clear()
    }

    /**
     * Clears all registered devices (useful between OpModes / tests).
     */
    fun clear() {
        closeAll()
    }

    /**
     * Publishes the current state of all registered hardware devices to the telemetry provider.
     */
    fun publishAll(telemetry: ITelemetry) {
        for ((name, device) in devices) {
            device.logTelemetry(telemetry, "Hardware/$name")
        }
    }
}
