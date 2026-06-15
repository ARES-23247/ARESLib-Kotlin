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

    private val lock = Any()
    private var running = true

    private var cachedRed = 0
    private var cachedGreen = 0
    private var cachedBlue = 0
    private var cachedAlpha = 0
    private var cachedNormalized = doubleArrayOf(0.0, 0.0, 0.0, 0.0)

    init {
        val thread = Thread {
            while (running) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                try {
                    r = sensor.red()
                    g = sensor.green()
                    b = sensor.blue()
                    a = sensor.alpha()
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
                        val sum = (r + g + b + a).toDouble()
                        normalized = if (sum < 0.1) {
                            doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                        } else {
                            doubleArrayOf(r / sum, g / sum, b / sum, a / sum)
                        }
                    }
                } catch (_: Exception) {
                    val sum = (r + g + b + a).toDouble()
                    normalized = if (sum < 0.1) {
                        doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                    } else {
                        doubleArrayOf(r / sum, g / sum, b / sum, a / sum)
                    }
                }

                synchronized(lock) {
                    cachedRed = r
                    cachedGreen = g
                    cachedBlue = b
                    cachedAlpha = a
                    cachedNormalized = normalized
                }

                try { Thread.sleep(20) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }
        }
        thread.isDaemon = true
        thread.name = "ARES-GenericColorSensor-Thread"
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

    fun close() {
        running = false
    }
}
