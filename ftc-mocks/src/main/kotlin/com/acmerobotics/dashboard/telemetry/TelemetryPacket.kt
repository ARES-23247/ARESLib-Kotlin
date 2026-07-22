package com.acmerobotics.dashboard.telemetry

import com.acmerobotics.dashboard.canvas.Canvas

/**
 * Mock representation of FTC Dashboard [TelemetryPacket].
 */
open class TelemetryPacket {
    val fieldOverlay = Canvas()
    /**
     * put declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun put(key: String, value: Any) {}
}
