package com.areslib.ftc.hardware

import com.areslib.hardware.sensor.DistanceSensorIO
import com.areslib.hardware.HardwareRegistry
import com.qualcomm.robotcore.hardware.DistanceSensor
import org.firstinspires.ftc.robotcore.external.navigation.Distance

/**
 * Wraps a standard FTC I2C/ToF/LiDAR distance sensor (e.g. REV 2m Distance Sensor, goBILDA ToF Sensor).
 */
class FtcDistanceSensor(private val sensor: DistanceSensor) : DistanceSensorIO, AutoCloseable {
    private val lock = Any()
    private var running = true
    private var cachedDistance = Double.NaN

    init {
        HardwareRegistry.registerCloseable(this)
        val thread = Thread {
            while (running) {
                val d = try {
                    sensor.getDistance(Distance.METER)
                } catch (_: Exception) {
                    Double.NaN
                }
                synchronized(lock) {
                    cachedDistance = d
                }
                try { Thread.sleep(20) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }
        }
        thread.isDaemon = true
        thread.name = "ARES-DistanceSensor-Thread"
        thread.start()
    }

    override val distanceMeters: Double
        get() = synchronized(lock) { cachedDistance }

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
        running = false
    }
}

