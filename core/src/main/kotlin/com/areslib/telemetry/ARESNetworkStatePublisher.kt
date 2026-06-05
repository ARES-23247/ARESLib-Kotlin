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

        // Detailed sub-states
        telemetry.putNumber("Superstructure/Flywheel/Velocity", state.superstructure.flywheel.velocityRpm)
        telemetry.putNumber("Superstructure/Flywheel/TargetVelocity", state.superstructure.flywheel.targetVelocityRpm)
        telemetry.putNumber("Superstructure/Cowl/Angle", state.superstructure.cowl.angleDegrees)
        telemetry.putNumber("Superstructure/Cowl/TargetAngle", state.superstructure.cowl.targetAngleDegrees)
        telemetry.putNumber("Superstructure/Intake/PivotAngle", state.superstructure.intake.pivotAngleDegrees)
        telemetry.putNumber("Superstructure/Intake/TargetAngle", state.superstructure.intake.targetAngleDegrees)
        telemetry.putBoolean("Superstructure/Feeder/PieceDetected", state.superstructure.feeder.gamePieceDetected)

        // ── Vision ──
        telemetry.putBoolean("Vision/HasTarget", state.vision.hasTarget)
        telemetry.putNumber("Vision/Target_X", state.vision.targetX)
        telemetry.putNumber("Vision/Target_Y", state.vision.targetY)
        telemetry.putNumber("Vision/MeasurementCount", state.vision.measurements.size.toDouble())

        // ── Gamepad 1 ──
        if (gamepad1 != null) {
            telemetry.putNumber("Gamepad1/LeftStick_X", gamepad1.leftStickX.toDouble())
            telemetry.putNumber("Gamepad1/LeftStick_Y", gamepad1.leftStickY.toDouble())
            telemetry.putNumber("Gamepad1/RightStick_X", gamepad1.rightStickX.toDouble())
            telemetry.putNumber("Gamepad1/RightStick_Y", gamepad1.rightStickY.toDouble())
            telemetry.putNumber("Gamepad1/LeftTrigger", gamepad1.leftTrigger.toDouble())
            telemetry.putNumber("Gamepad1/RightTrigger", gamepad1.rightTrigger.toDouble())
            telemetry.putBoolean("Gamepad1/A", gamepad1.a)
            telemetry.putBoolean("Gamepad1/B", gamepad1.b)
            telemetry.putBoolean("Gamepad1/X", gamepad1.x)
            telemetry.putBoolean("Gamepad1/Y", gamepad1.y)
            telemetry.putBoolean("Gamepad1/DpadUp", gamepad1.dpadUp)
            telemetry.putBoolean("Gamepad1/DpadDown", gamepad1.dpadDown)
            telemetry.putBoolean("Gamepad1/DpadLeft", gamepad1.dpadLeft)
            telemetry.putBoolean("Gamepad1/DpadRight", gamepad1.dpadRight)
            telemetry.putBoolean("Gamepad1/LeftBumper", gamepad1.leftBumper)
            telemetry.putBoolean("Gamepad1/RightBumper", gamepad1.rightBumper)
        }

        // ── Gamepad 2 ──
        if (gamepad2 != null) {
            telemetry.putNumber("Gamepad2/LeftStick_X", gamepad2.leftStickX.toDouble())
            telemetry.putNumber("Gamepad2/LeftStick_Y", gamepad2.leftStickY.toDouble())
            telemetry.putNumber("Gamepad2/RightStick_X", gamepad2.rightStickX.toDouble())
            telemetry.putNumber("Gamepad2/RightStick_Y", gamepad2.rightStickY.toDouble())
            telemetry.putNumber("Gamepad2/LeftTrigger", gamepad2.leftTrigger.toDouble())
            telemetry.putNumber("Gamepad2/RightTrigger", gamepad2.rightTrigger.toDouble())
            telemetry.putBoolean("Gamepad2/A", gamepad2.a)
            telemetry.putBoolean("Gamepad2/B", gamepad2.b)
            telemetry.putBoolean("Gamepad2/X", gamepad2.x)
            telemetry.putBoolean("Gamepad2/Y", gamepad2.y)
            telemetry.putBoolean("Gamepad2/DpadUp", gamepad2.dpadUp)
            telemetry.putBoolean("Gamepad2/DpadDown", gamepad2.dpadDown)
            telemetry.putBoolean("Gamepad2/DpadLeft", gamepad2.dpadLeft)
            telemetry.putBoolean("Gamepad2/DpadRight", gamepad2.dpadRight)
            telemetry.putBoolean("Gamepad2/LeftBumper", gamepad2.leftBumper)
            telemetry.putBoolean("Gamepad2/RightBumper", gamepad2.rightBumper)
        }

        telemetry.update()
    }
}
