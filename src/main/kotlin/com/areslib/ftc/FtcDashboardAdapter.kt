package com.areslib.ftc

import com.qualcomm.robotcore.hardware.FtcDashboard
import com.qualcomm.robotcore.hardware.TelemetryPacket
import com.areslib.state.RobotState
import kotlin.math.cos
import kotlin.math.sin

object FtcDashboardAdapter {
    /**
     * Translates a pure RobotState into an FtcDashboard TelemetryPacket without side effects.
     */
    fun createPacket(state: RobotState): TelemetryPacket {
        val packet = TelemetryPacket()
        
        // Add scalar telemetry
        packet.put("Odom_X", state.drive.odometryX)
        packet.put("Odom_Y", state.drive.odometryY)
        packet.put("Odom_Heading", state.drive.odometryHeading)
        packet.put("Drive_Vx", state.drive.xVelocityMetersPerSecond)
        packet.put("Drive_Vy", state.drive.yVelocityMetersPerSecond)
        packet.put("Drive_Omega", state.drive.angularVelocityRadiansPerSecond)
        
        // Draw Robot Pose on Field Overlay
        val canvas = packet.fieldOverlay
        canvas.setStroke("#4CAF50")
        
        // FtcDashboard canvas units are typically inches, we assume state is meters.
        // For drawing, we'll scale meters to inches: 1 meter = 39.37 inches.
        val inchesX = state.drive.odometryX * 39.37
        val inchesY = state.drive.odometryY * 39.37
        
        // Draw robot body (radius 9 inches)
        canvas.drawCircle(inchesX, inchesY, 9.0)
        
        // Draw heading line
        val headingLineEndX = inchesX + 9.0 * cos(state.drive.odometryHeading)
        val headingLineEndY = inchesY + 9.0 * sin(state.drive.odometryHeading)
        canvas.drawLine(inchesX, inchesY, headingLineEndX, headingLineEndY)
        
        return packet
    }

    /**
     * Sends the current state to the dashboard.
     */
    fun sendState(state: RobotState) {
        val packet = createPacket(state)
        FtcDashboard.getInstance().sendTelemetryPacket(packet)
    }
}
