package com.acmerobotics.dashboard.telemetry

import com.acmerobotics.dashboard.canvas.Canvas

/**
 * Mock representation of FTC Dashboard [TelemetryPacket].
 */
open class TelemetryPacket {
    val fieldOverlay = Canvas()
    /**
     * put declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun put(key: String, value: Any) {}
}
