package com.areslib.ftc.hardware

import com.areslib.hardware.ColorSensorIO
import com.areslib.hardware.DistanceSensorIO
import com.qualcomm.robotcore.hardware.ColorSensor
import com.qualcomm.robotcore.hardware.DistanceSensor
import com.qualcomm.robotcore.hardware.NormalizedColorSensor
import org.firstinspires.ftc.robotcore.external.navigation.Distance

/**
 * Integrated wrapper for the REV Color Sensor V3.
 * 
 * Since the REV V3 physical hardware contains both a multi-spectral color sensor 
 * and an infrared proximity/rangefinder, this class implements both ColorSensorIO 
 * and DistanceSensorIO for unified, frictionless reading.
 */
class FtcRevColorSensorV3(private val device: ColorSensor) : ColorSensorIO, DistanceSensorIO {
    
    private val normalizedSensor = device as? NormalizedColorSensor
    private val distanceSensor = device as? DistanceSensor

    override val red: Int
        get() = device.red()
    override val green: Int
        get() = device.green()
    override val blue: Int
        get() = device.blue()
    override val alpha: Int
        get() = device.alpha()

    override val normalizedRgb: DoubleArray
        get() {
            val colors = normalizedSensor?.normalizedColors
            if (colors != null) {
                return doubleArrayOf(
                    colors.red.toDouble(),
                    colors.green.toDouble(),
                    colors.blue.toDouble(),
                    colors.alpha.toDouble()
                )
            }
            val sum = (red + green + blue + alpha).toDouble()
            if (sum < 0.1) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            return doubleArrayOf(red / sum, green / sum, blue / sum, alpha / sum)
        }

    /**
     * Reads the integrated proximity rangefinder distance in meters.
     */
    override val distanceMeters: Double
        get() = distanceSensor?.getDistance(Distance.METER) ?: Double.NaN
}
