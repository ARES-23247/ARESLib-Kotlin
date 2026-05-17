package com.areslib.ftc.hardware

import com.areslib.hardware.DistanceSensorIO
import com.qualcomm.robotcore.hardware.DistanceSensor
import org.firstinspires.ftc.robotcore.external.navigation.Distance

/**
 * Wraps a standard FTC I2C/ToF/LiDAR distance sensor (e.g. REV 2m Distance Sensor, goBILDA ToF Sensor).
 */
class FtcDistanceSensor(private val sensor: DistanceSensor) : DistanceSensorIO {
    /**
     * Reads the current distance in meters from the physical sensor.
     */
    override val distanceMeters: Double
        get() = sensor.getDistance(Distance.METER)
}
