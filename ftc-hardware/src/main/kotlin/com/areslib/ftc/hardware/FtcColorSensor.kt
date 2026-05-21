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
        get() = try { sensor.red() } catch (_: Exception) { 0 }
    override val green: Int
        get() = try { sensor.green() } catch (_: Exception) { 0 }
    override val blue: Int
        get() = try { sensor.blue() } catch (_: Exception) { 0 }
    override val alpha: Int
        get() = try { sensor.alpha() } catch (_: Exception) { 0 }

    override val normalizedRgb: DoubleArray
        get() {
            try {
                val colors = normalizedSensor?.normalizedColors
                if (colors != null) {
                    return doubleArrayOf(
                        colors.red.toDouble(),
                        colors.green.toDouble(),
                        colors.blue.toDouble(),
                        colors.alpha.toDouble()
                    )
                }
            } catch (_: Exception) {}
            // Math fallback for legacy/mock sensors lacking the normalized interface
            val r = red
            val g = green
            val b = blue
            val a = alpha
            val sum = (r + g + b + a).toDouble()
            if (sum < 0.1) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            return doubleArrayOf(r / sum, g / sum, b / sum, a / sum)
        }
}
