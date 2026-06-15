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

    private var lastUpdateMs = 0L
    private var cachedRed = 0
    private var cachedGreen = 0
    private var cachedBlue = 0
    private var cachedAlpha = 0
    private var cachedNormalized = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
    private var cachedDistance = Double.NaN

    @Synchronized
    private fun updateIfStale() {
        val now = RobotClock.currentTimeMillis()
        if (now - lastUpdateMs < 20) return
        lastUpdateMs = now

        // Fetch color sensor data
        try {
            cachedRed = device.red()
            cachedGreen = device.green()
            cachedBlue = device.blue()
            cachedAlpha = device.alpha()
        } catch (_: Exception) {
            cachedRed = 0
            cachedGreen = 0
            cachedBlue = 0
            cachedAlpha = 0
        }

        // Fetch normalized RGB data
        try {
            val colors = normalizedSensor?.normalizedColors
            if (colors != null) {
                cachedNormalized = doubleArrayOf(
                    colors.red.toDouble(),
                    colors.green.toDouble(),
                    colors.blue.toDouble(),
                    colors.alpha.toDouble()
                )
            } else {
                val sum = (cachedRed + cachedGreen + cachedBlue + cachedAlpha).toDouble()
                cachedNormalized = if (sum < 0.1) {
                    doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                } else {
                    doubleArrayOf(cachedRed / sum, cachedGreen / sum, cachedBlue / sum, cachedAlpha / sum)
                }
            }
        } catch (_: Exception) {
            val sum = (cachedRed + cachedGreen + cachedBlue + cachedAlpha).toDouble()
            cachedNormalized = if (sum < 0.1) {
                doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            } else {
                doubleArrayOf(cachedRed / sum, cachedGreen / sum, cachedBlue / sum, cachedAlpha / sum)
            }
        }

        // Fetch distance sensor data
        try {
            cachedDistance = distanceSensor?.getDistance(Distance.METER) ?: Double.NaN
        } catch (_: Exception) {
            cachedDistance = Double.NaN
        }
    }

    override val red: Int
        get() {
            updateIfStale()
            return cachedRed
        }
    override val green: Int
        get() {
            updateIfStale()
            return cachedGreen
        }
    override val blue: Int
        get() {
            updateIfStale()
            return cachedBlue
        }
    override val alpha: Int
        get() {
            updateIfStale()
            return cachedAlpha
        }

    override val normalizedRgb: DoubleArray
        get() {
            updateIfStale()
            return cachedNormalized
        }

    /**
     * Reads the integrated proximity rangefinder distance in meters.
     */
    override val distanceMeters: Double
        get() {
            updateIfStale()
            return cachedDistance
        }
}
