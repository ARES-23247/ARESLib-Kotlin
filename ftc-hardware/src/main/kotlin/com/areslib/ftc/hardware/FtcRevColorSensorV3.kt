package com.areslib.ftc.hardware

import com.areslib.util.RobotClock

import com.areslib.hardware.sensor.ColorSensorIO
import com.areslib.hardware.sensor.DistanceSensorIO
import com.areslib.hardware.HardwareRegistry
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
class FtcRevColorSensorV3(private val device: ColorSensor) : ColorSensorIO, DistanceSensorIO, AutoCloseable {
    
    private val normalizedSensor = device as? NormalizedColorSensor
    private val distanceSensor = device as? DistanceSensor

    private val lock = Any()
    private var running = true

    private var cachedRed = 0
    private var cachedGreen = 0
    private var cachedBlue = 0
    private var cachedAlpha = 0
    private val cachedNormalized = DoubleArray(4)
    private var cachedDistance = Double.NaN
    private val threadBuffer = DoubleArray(4)

    init {
        HardwareRegistry.registerCloseable(this)
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

                try {
                    val colors = normalizedSensor?.normalizedColors
                    if (colors != null) {
                        threadBuffer[0] = colors.red.toDouble()
                        threadBuffer[1] = colors.green.toDouble()
                        threadBuffer[2] = colors.blue.toDouble()
                        threadBuffer[3] = colors.alpha.toDouble()
                    } else {
                        val sum = (red + green + blue + alpha).toDouble()
                        if (sum < 0.1) {
                            threadBuffer[0] = 0.0
                            threadBuffer[1] = 0.0
                            threadBuffer[2] = 0.0
                            threadBuffer[3] = 0.0
                        } else {
                            threadBuffer[0] = red / sum
                            threadBuffer[1] = green / sum
                            threadBuffer[2] = blue / sum
                            threadBuffer[3] = alpha / sum
                        }
                    }
                } catch (_: Exception) {
                    val sum = (red + green + blue + alpha).toDouble()
                    if (sum < 0.1) {
                        threadBuffer[0] = 0.0
                        threadBuffer[1] = 0.0
                        threadBuffer[2] = 0.0
                        threadBuffer[3] = 0.0
                    } else {
                        threadBuffer[0] = red / sum
                        threadBuffer[1] = green / sum
                        threadBuffer[2] = blue / sum
                        threadBuffer[3] = alpha / sum
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
                    System.arraycopy(threadBuffer, 0, cachedNormalized, 0, 4)
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

    /**
     * close declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        running = false
    }
}

