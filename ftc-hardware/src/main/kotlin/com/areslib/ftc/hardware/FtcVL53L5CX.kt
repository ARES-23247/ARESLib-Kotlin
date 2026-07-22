package com.areslib.ftc.hardware

import com.areslib.hardware.sensor.MultizoneDistanceSensorIO

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

    private val lock = Any()
    private var running = true
    private var lastDistances = DoubleArray(0)

    init {
        val thread = Thread {
            while (running) {
                try {
                    driver.update()
                    val raw = driver.distancesMillimeters
                    val dists = DoubleArray(raw.size)
                    for (i in raw.indices) {
                        dists[i] = raw[i] / 1000.0
                    }
                    synchronized(lock) {
                        lastDistances = dists
                    }
                } catch (_: Exception) {}
                try { Thread.sleep(20) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }
        }
        thread.isDaemon = true
        thread.name = "ARES-VL53L5CX-Thread"
        thread.start()
    }

    override val distancesMeters: DoubleArray
        get() = synchronized(lock) { lastDistances }

    /**
     * close declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun close() {
        running = false
    }
}

