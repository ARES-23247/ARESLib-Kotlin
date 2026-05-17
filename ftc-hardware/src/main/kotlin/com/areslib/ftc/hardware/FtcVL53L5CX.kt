package com.areslib.ftc.hardware

import com.areslib.hardware.MultizoneDistanceSensorIO

/**
 * Proxy interface that mirrors typical community-made VL53L5CX I2C drivers.
 * Allows any open-source or custom driver to be cleanly wrapped into ARESLib.
 */
interface VL53L5CXDriverProxy {
    /**
     * Polls the physical I2C sensor for the latest frame of zone distances.
     */
    fun update()

    /**
     * Active row resolution (usually 4 or 8).
     */
    val rows: Int

    /**
     * Active column resolution (usually 4 or 8).
     */
    val columns: Int

    /**
     * Raw zone distance readings in millimeters.
     */
    val distancesMillimeters: IntArray
}

/**
 * Concrete driver wrapper for a VL53L5CX multizone distance sensor.
 */
class FtcVL53L5CX(private val driver: VL53L5CXDriverProxy) : MultizoneDistanceSensorIO {
    override val rows: Int
        get() = driver.rows

    override val columns: Int
        get() = driver.columns

    override val distancesMeters: DoubleArray
        get() {
            driver.update()
            val raw = driver.distancesMillimeters
            val converted = DoubleArray(raw.size)
            for (i in raw.indices) {
                converted[i] = raw[i] / 1000.0 // Convert mm to meters
            }
            return converted
        }
}
