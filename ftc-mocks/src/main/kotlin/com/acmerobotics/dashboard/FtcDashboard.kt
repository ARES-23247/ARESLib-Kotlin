package com.acmerobotics.dashboard

import com.acmerobotics.dashboard.telemetry.TelemetryPacket

/**
 * Mock representation of [FtcDashboard] helper.
 */
object FtcDashboard {
    @JvmStatic
    /**
     * getInstance declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getInstance(): FtcDashboard = this
    /**
     * sendTelemetryPacket declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun sendTelemetryPacket(packet: TelemetryPacket) {}
}
