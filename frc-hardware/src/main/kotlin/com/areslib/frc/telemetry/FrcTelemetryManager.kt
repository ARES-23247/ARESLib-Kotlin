package com.areslib.frc.telemetry

import com.areslib.control.BrownoutGuard
import com.areslib.frc.telemetry.MarvinStatePublisher
import com.areslib.frc.SwerveHardwareIO
import com.areslib.state.RobotState
import com.areslib.subsystem.Store
import com.areslib.telemetry.*
import com.ctre.phoenix6.CANBus

class FrcTelemetryManager(
    baseTelemetry: ITelemetry,
    private val store: Store,
    private val swerveIO: SwerveHardwareIO? = null
) : AutoCloseable {

    // Unified telemetry pipeline: base telemetry → CSV wrapper → publisher
    val dataLoggingTelemetry = DataLoggingTelemetry(baseTelemetry)
    val publisher = ARESNetworkStatePublisher(dataLoggingTelemetry)

    // Pre-allocated buffers to prevent high-frequency GC allocations in update loop
    private val covarianceDiagonals = DoubleArray(3)
    private val swerveStates = DoubleArray(8)
    private val swerveFaults = IntArray(4)

    // CANBus instance created once to avoid allocations in update loop
    private val canBus = CANBus("CAN2")

    /**
     * Publishes core robot state, Marvin XIX specific sub-states, and AdvantageScope 3D visualization topics.
     */
    fun publish(gamepad1: GamepadState? = null, gamepad2: GamepadState? = null) {
        val state = store.state
        publisher.publish(state, gamepad1, gamepad2)

        // Publish Marvin XIX specific sub-states
        MarvinStatePublisher.publish(state, dataLoggingTelemetry)

        // Publish EKF covariance diagonals
        val cov = state.drive.poseEstimator.covariance
        covarianceDiagonals[0] = cov.m00
        covarianceDiagonals[1] = cov.m11
        covarianceDiagonals[2] = cov.m22
        dataLoggingTelemetry.putDoubleArray("Robot/Odometry/Covariance", covarianceDiagonals)

        // Publish 3D robot pose (quaternion format for AdvantageScope)
        dataLoggingTelemetry.logPose3d(
            "Robot/Pose3d",
            state.drive.odometryX,
            state.drive.odometryY,
            state.drive.odometryHeading
        )

        // Publish swerve module states
        val vx = state.drive.xVelocityMetersPerSecond
        val vy = state.drive.yVelocityMetersPerSecond
        val omega = state.drive.angularVelocityRadiansPerSecond
        for (i in 0..3) {
            val wvx = vx - omega * SWERVE_OFFSETS[i].second
            val wvy = vy + omega * SWERVE_OFFSETS[i].first
            swerveStates[i * 2] = Math.atan2(wvy, wvx)
            swerveStates[i * 2 + 1] = Math.hypot(wvx, wvy)
        }
        dataLoggingTelemetry.putDoubleArray("Robot/SwerveStates", swerveStates)

        // --- Log FRC CANbus Diagnostics & Latency ---
        try {
            val canStatus = canBus.status
            val latencyMs = swerveIO?.signalLatencyMs ?: 0.0

            dataLoggingTelemetry.logCanBusStatus(
                busName = "CAN2",
                busUtilization = canStatus.BusUtilization.toDouble(),
                errorCount = canStatus.REC + canStatus.TEC,
                txErrors = canStatus.TEC,
                rxErrors = canStatus.REC,
                busOffs = canStatus.BusOffCount,
                signalLatencyMs = latencyMs
            )

            // Log Swerve Motor Active Faults
            if (swerveIO != null) {
                swerveIO.getFaults(swerveFaults)
                for (i in 0..3) {
                    dataLoggingTelemetry.putNumber("Diagnostics/Motor/Swerve_$i/Faults", swerveFaults[i].toDouble())
                }
            }
        } catch (_: Throwable) {
            // Graceful fallback if CANBus API fails (e.g. in simulation)
        }
    }

    /**
     * Publishes brownout telemetry.
     */
    fun logBrownout(brownoutGuard: BrownoutGuard, batteryVoltage: Double) {
        dataLoggingTelemetry.logBrownout(brownoutGuard, batteryVoltage)
    }

    override fun close() {
        dataLoggingTelemetry.close()
    }

    companion object {
        private val SWERVE_OFFSETS = arrayOf(
            Pair(0.35, 0.35), Pair(0.35, -0.35),
            Pair(-0.35, 0.35), Pair(-0.35, -0.35)
        )
    }
}
