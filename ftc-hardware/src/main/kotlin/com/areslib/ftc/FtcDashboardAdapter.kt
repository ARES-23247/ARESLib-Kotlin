package com.areslib.ftc

import com.qualcomm.robotcore.hardware.FtcDashboard
import com.qualcomm.robotcore.hardware.TelemetryPacket
import com.areslib.state.RobotState
import kotlin.math.cos
import kotlin.math.sin

@Deprecated("Use ARESNetworkStatePublisher and NT4Telemetry instead for pure cross-platform architecture")
object FtcDashboardAdapter {
    /**
     * Translates a pure RobotState into an FtcDashboard TelemetryPacket without side effects.
     */
    fun createPacket(state: RobotState): TelemetryPacket {
        val packet = TelemetryPacket()
        
        // Add raw pinpoint telemetry
        packet.put("Odom_X", state.drive.odometryX)
        packet.put("Odom_Y", state.drive.odometryY)
        packet.put("Odom_Heading", state.drive.odometryHeading)

        // Add EKF telemetry
        packet.put("Pose_X", state.drive.poseEstimator.estimatedPose.x)
        packet.put("Pose_Y", state.drive.poseEstimator.estimatedPose.y)
        packet.put("Pose_Heading", state.drive.poseEstimator.estimatedPose.heading.radians)

        packet.put("Drive_Vx", state.drive.xVelocityMetersPerSecond)
        packet.put("Drive_Vy", state.drive.yVelocityMetersPerSecond)
        packet.put("Drive_Omega", state.drive.angularVelocityRadiansPerSecond)
        
        val canvas = packet.fieldOverlay

        // 1. Draw Raw Pinpoint Odometry (Orange-Red)
        canvas.setStroke("#FF5722")
        val rawInchesX = state.drive.odometryX * 39.37
        val rawInchesY = state.drive.odometryY * 39.37
        canvas.drawCircle(rawInchesX, rawInchesY, 9.0)
        val rawHeadingLineEndX = rawInchesX + 9.0 * cos(state.drive.odometryHeading)
        val rawHeadingLineEndY = rawInchesY + 9.0 * sin(state.drive.odometryHeading)
        canvas.drawLine(rawInchesX, rawInchesY, rawHeadingLineEndX, rawHeadingLineEndY)

        // 2. Draw Fused EKF Pose (Green)
        canvas.setStroke("#4CAF50")
        val ekfInchesX = state.drive.poseEstimator.estimatedPose.x * 39.37
        val ekfInchesY = state.drive.poseEstimator.estimatedPose.y * 39.37
        canvas.drawCircle(ekfInchesX, ekfInchesY, 9.0)
        val ekfHeadingLineEndX = ekfInchesX + 9.0 * cos(state.drive.poseEstimator.estimatedPose.heading.radians)
        val ekfHeadingLineEndY = ekfInchesY + 9.0 * sin(state.drive.poseEstimator.estimatedPose.heading.radians)
        canvas.drawLine(ekfInchesX, ekfInchesY, ekfHeadingLineEndX, ekfHeadingLineEndY)
        
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
