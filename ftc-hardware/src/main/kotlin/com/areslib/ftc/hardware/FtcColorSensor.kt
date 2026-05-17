package com.areslib.ftc.hardware

import com.areslib.hardware.ColorSensorIO
import com.qualcomm.robotcore.hardware.ColorSensor
import com.qualcomm.robotcore.hardware.NormalizedColorSensor

/**
 * Wraps a generic FTC Color Sensor.
 * Automatically attempts to use NormalizedColorSensor telemetry for accurate, lighting-invariant detection.
 */
class FtcColorSensor(private val sensor: ColorSensor) : ColorSensorIO {
    
    private val normalizedSensor = sensor as? NormalizedColorSensor

    override val red: Int
        get() = sensor.red()
    override val green: Int
        get() = sensor.green()
    override val blue: Int
        get() = sensor.blue()
    override val alpha: Int
        get() = sensor.alpha()

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
            // Math fallback for legacy/mock sensors lacking the normalized interface
            val sum = (red + green + blue + alpha).toDouble()
            if (sum < 0.1) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            return doubleArrayOf(red / sum, green / sum, blue / sum, alpha / sum)
        }
}
