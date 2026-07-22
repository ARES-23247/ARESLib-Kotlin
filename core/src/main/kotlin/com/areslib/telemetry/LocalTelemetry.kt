package com.areslib.telemetry

/**
 * Lightweight abstraction for human-readable driver station telemetry.
 * Prevents reflection overhead in hardware facades while maintaining FTC OpMode independence.
 */
interface LocalTelemetry {
    /**
     * addData declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun addData(caption: String, value: Any?)
    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun update()
}
