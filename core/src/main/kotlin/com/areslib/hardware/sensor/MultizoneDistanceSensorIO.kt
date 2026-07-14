package com.areslib.hardware.sensor

/**
 * Pure abstraction for reading a multizone Time-of-Flight rangefinder (like the VL53L5CX 8x8 or 4x4 sensor).
 * This makes multizone distance tracking mockable, simulation-friendly, and decoupled.
 */
interface MultizoneDistanceSensorIO {
    /**
     * Number of grid rows (e.g., 4 or 8).
     */
    val rows: Int

    /**
     * Number of grid columns (e.g., 4 or 8).
     */
    val columns: Int

    /**
     * Flattened array of distances in meters for each zone.
     * The length of this array is [rows] * [columns].
     */
    val distancesMeters: DoubleArray
}
