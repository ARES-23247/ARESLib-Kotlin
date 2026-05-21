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
        get() = try { driver.rows } catch (_: Exception) { 0 }

    override val columns: Int
        get() = try { driver.columns } catch (_: Exception) { 0 }

    private var lastDistances = DoubleArray(0)

    override val distancesMeters: DoubleArray
        get() {
            try {
                driver.update()
                val raw = driver.distancesMillimeters
                if (raw.size != lastDistances.size) {
                    lastDistances = DoubleArray(raw.size)
                }
                for (i in raw.indices) {
                    lastDistances[i] = raw[i] / 1000.0 // Convert mm to meters
                }
            } catch (_: Exception) {}
            return lastDistances
        }
}
