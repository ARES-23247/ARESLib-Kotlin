package com.areslib.ftc.hardware

import com.areslib.util.RobotClock

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

    private val lock = Any()
    private var running = true

    private var cachedRed = 0
    private var cachedGreen = 0
    private var cachedBlue = 0
    private var cachedAlpha = 0
    private var cachedNormalized = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
    private var cachedDistance = Double.NaN

    init {
        val thread = Thread {
            while (running) {
                var red = 0
                var green = 0
                var blue = 0
                var alpha = 0
                try {
                    red = device.red()
                    green = device.green()
                    blue = device.blue()
                    alpha = device.alpha()
                } catch (_: Exception) {}

                var normalized = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                try {
                    val colors = normalizedSensor?.normalizedColors
                    if (colors != null) {
                        normalized = doubleArrayOf(
                            colors.red.toDouble(),
                            colors.green.toDouble(),
                            colors.blue.toDouble(),
                            colors.alpha.toDouble()
                        )
                    } else {
                        val sum = (red + green + blue + alpha).toDouble()
                        normalized = if (sum < 0.1) {
                            doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                        } else {
                            doubleArrayOf(red / sum, green / sum, blue / sum, alpha / sum)
                        }
                    }
                } catch (_: Exception) {
                    val sum = (red + green + blue + alpha).toDouble()
                    normalized = if (sum < 0.1) {
                        doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                    } else {
                        doubleArrayOf(red / sum, green / sum, blue / sum, alpha / sum)
                    }
                }

                var distance = Double.NaN
                try {
                    distance = distanceSensor?.getDistance(Distance.METER) ?: Double.NaN
                } catch (_: Exception) {}

                synchronized(lock) {
                    cachedRed = red
                    cachedGreen = green
                    cachedBlue = blue
                    cachedAlpha = alpha
                    cachedNormalized = normalized
                    cachedDistance = distance
                }

                try { Thread.sleep(20) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }
        }
        thread.isDaemon = true
        thread.name = "ARES-ColorSensorV3-Thread"
        thread.start()
    }

    override val red: Int
        get() = synchronized(lock) { cachedRed }
    override val green: Int
        get() = synchronized(lock) { cachedGreen }
    override val blue: Int
        get() = synchronized(lock) { cachedBlue }
    override val alpha: Int
        get() = synchronized(lock) { cachedAlpha }

    override val normalizedRgb: DoubleArray
        get() = synchronized(lock) { cachedNormalized }

    /**
     * Reads the integrated proximity rangefinder distance in meters.
     */
    override val distanceMeters: Double
        get() = synchronized(lock) { cachedDistance }

    fun close() {
        running = false
    }
}
