package com.areslib.telemetry

import com.areslib.state.RobotState

/**
 * Serializes and publishes the complete RobotState to an ITelemetry interface.
 * Covers drive, superstructure, vision, and optional gamepad inputs so that
 * every robot built with ARESLib gets full logging and replay for free.
 */
class ARESNetworkStatePublisher(private val telemetry: ITelemetry) {

    fun publish(
        state: RobotState,
        gamepad1: GamepadState? = null,
        gamepad2: GamepadState? = null
    ) {
        // ── Drive ──
        // Raw Pinpoint Odometry
        telemetry.putNumber("Drive/Odom_X", state.drive.odometryX)
        telemetry.putNumber("Drive/Odom_Y", state.drive.odometryY)
        telemetry.putNumber("Drive/Odom_Heading", state.drive.odometryHeading)

        // Fused EKF Estimated Pose
        telemetry.putNumber("Drive/Pose_X", state.drive.poseEstimator.estimatedPose.x)
        telemetry.putNumber("Drive/Pose_Y", state.drive.poseEstimator.estimatedPose.y)
        telemetry.putNumber("Drive/Drive_Heading", state.drive.poseEstimator.estimatedPose.heading.radians)

        telemetry.putNumber("Drive/Velocity_X", state.drive.xVelocityMetersPerSecond)
        telemetry.putNumber("Drive/Velocity_Y", state.drive.yVelocityMetersPerSecond)
        telemetry.putNumber("Drive/Velocity_Omega", state.drive.angularVelocityRadiansPerSecond)

        // Pose2d for AdvantageScope 3D visualization
        // Main robot pose tracks the EKF-fused pose (used by robot pathing/aiming)
        telemetry.putDoubleArray(
            "AdvantageScope/RobotPose",
            doubleArrayOf(
                state.drive.poseEstimator.estimatedPose.x,
                state.drive.poseEstimator.estimatedPose.y,
                state.drive.poseEstimator.estimatedPose.heading.radians
            )
        )
        // Raw pinpoint odometry is published separately to allow comparing drift
        telemetry.putDoubleArray(
            "AdvantageScope/RawOdomPose",
            doubleArrayOf(
                state.drive.odometryX,
                state.drive.odometryY,
                state.drive.odometryHeading
            )
        )

        // ── Superstructure ──
        telemetry.putString("Superstructure/Mode", state.superstructure.mode.name)
        telemetry.putNumber("Superstructure/Flywheel_RPM", state.superstructure.flywheelRPM)
        telemetry.putNumber("Superstructure/Flywheel_Target", state.superstructure.flywheelTargetRPM)
        telemetry.putBoolean("Superstructure/Flywheel_Active", state.superstructure.flywheelActive)
        telemetry.putBoolean("Superstructure/Flywheel_AtSpeed", state.superstructure.isFlywheelAtSpeed)
        telemetry.putBoolean("Superstructure/Intake_Active", state.superstructure.intakeActive)
        telemetry.putBoolean("Superstructure/Transfer_Active", state.superstructure.transferActive)
        telemetry.putNumber("Superstructure/Elevator_Height", state.superstructure.elevatorHeightMeters)
        telemetry.putNumber("Superstructure/Inventory", state.superstructure.inventoryCount.toDouble())



        // ── Vision ──
        telemetry.putBoolean("Vision/HasTarget", state.vision.hasTarget)
        telemetry.putNumber("Vision/Target_X", state.vision.targetX)
        telemetry.putNumber("Vision/Target_Y", state.vision.targetY)
        telemetry.putNumber("Vision/MeasurementCount", state.vision.measurements.size.toDouble())

        // ── Gamepad 1 ──
        gamepad1?.let { telemetry.logGamepad("Gamepad1", it) }

        // ── Gamepad 2 ──
        gamepad2?.let { telemetry.logGamepad("Gamepad2", it) }

        telemetry.update()
    }
}
