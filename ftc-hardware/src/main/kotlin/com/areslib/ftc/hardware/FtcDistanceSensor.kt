package com.areslib.ftc.hardware

import com.areslib.hardware.DistanceSensorIO
import com.qualcomm.robotcore.hardware.DistanceSensor
import org.firstinspires.ftc.robotcore.external.navigation.Distance

/**
 * Wraps a standard FTC I2C/ToF/LiDAR distance sensor (e.g. REV 2m Distance Sensor, goBILDA ToF Sensor).
 */
class FtcDistanceSensor(private val sensor: DistanceSensor) : DistanceSensorIO {
    private val lock = Any()
    private var running = true
    private var cachedDistance = Double.NaN

    init {
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

    fun close() {
        running = false
    }
}
