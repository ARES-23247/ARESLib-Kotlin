package com.acmerobotics.dashboard

import com.acmerobotics.dashboard.telemetry.TelemetryPacket

/**
 * Mock representation of [FtcDashboard] helper.
 */
object FtcDashboard {
    @JvmStatic
    /**
     * getInstance declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getInstance(): FtcDashboard = this
    /**
     * sendTelemetryPacket declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun sendTelemetryPacket(packet: TelemetryPacket) {}
}
