package com.areslib.ftc.hardware

import com.areslib.hardware.DistanceSensorIO
import com.qualcomm.robotcore.hardware.DistanceSensor
import org.firstinspires.ftc.robotcore.external.navigation.Distance

/**
 * Wraps a standard FTC I2C/ToF/LiDAR distance sensor (e.g. REV 2m Distance Sensor, goBILDA ToF Sensor).
 */
class FtcDistanceSensor(private val sensor: DistanceSensor) : DistanceSensorIO {
    override val distanceMeters: Double
        get() = try {
            sensor.getDistance(Distance.METER)
        } catch (_: Exception) {
            Double.NaN
        }
}
