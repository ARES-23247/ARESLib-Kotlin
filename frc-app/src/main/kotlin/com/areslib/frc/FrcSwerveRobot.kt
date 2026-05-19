package com.areslib.frc

import com.areslib.action.RobotAction
import com.areslib.hardware.FlywheelIO
import com.areslib.hardware.CowlIO
import com.areslib.hardware.IntakeIO
import com.areslib.hardware.FeederIO
import com.areslib.state.DriveState
import com.areslib.subsystem.AresRobot
import com.areslib.telemetry.ARESNetworkStatePublisher
import com.areslib.telemetry.DataLoggingTelemetry
import com.areslib.telemetry.GamepadState
import com.areslib.telemetry.ITelemetry

import com.ctre.phoenix6.mechanisms.swerve.SwerveDrivetrain

/**
 * FRC Swerve Robot facade — the FRC mirror of FtcMecanumRobot.
 *
 * Extends AresRobot for the Store + subsystem facades, and pipes all telemetry
 * through the unified ARESNetworkStatePublisher for automatic logging and replay.
 *
 * Usage in a TimedRobot is identical to FtcMecanumRobot in a LinearOpMode:
 * ```
 * robot.update(controller.toState())
 * robot.drive.joystickDrive(forward, strafe, rotation)
 * robot.shooter.spinUp(4000.0)
 * ```
 */
class FrcSwerveRobot(
    private val swerveIO: FRCSwerveHardwareIO<*>? = null,
    private val flywheelIO: FlywheelIO,
    private val cowlIO: CowlIO,
    private val intakeIO: IntakeIO,
    private val feederIO: FeederIO,
    private val isSimulation: Boolean = false,
    baseTelemetry: ITelemetry = FRCTelemetry()
) : AresRobot() {

    // Unified telemetry pipeline: base telemetry → CSV wrapper → publisher
    private val dataLoggingTelemetry = DataLoggingTelemetry(baseTelemetry)
    private val publisher = ARESNetworkStatePublisher(dataLoggingTelemetry)

    /** Direct access to the underlying telemetry for custom keys (3D viz, etc). */
    val telemetry: ITelemetry get() = dataLoggingTelemetry

    /**
     * Coordinated frame update for FRC Swerve Drivetrain.
     *
     * 1. Reads hardware sensors → dispatches to Store
     * 2. Writes Store state → hardware outputs
     * 3. Publishes everything through unified pipeline (NT4 + CSV)
     *
     * @param gamepad1 Optional driver gamepad (use `controller.toState()`)
     * @param gamepad2 Optional operator gamepad
     */
    fun update(gamepad1: GamepadState? = null, gamepad2: GamepadState? = null) {
        val timestamp = System.currentTimeMillis()

        // ── 1. READ: Hardware → Store ──
        if (!isSimulation && swerveIO != null) {
            val driveState = swerveIO.read()
            store.dispatch(RobotAction.PoseUpdate(
                xMeters = driveState.odometryX,
                yMeters = driveState.odometryY,
                headingRadians = driveState.odometryHeading,
                timestampMs = timestamp
            ))
        }

        // Read superstructure sensors
        store.dispatch(RobotAction.SuperstructureSensorUpdate(
            flywheelRpm = flywheelIO.velocityRpm,
            cowlAngle = cowlIO.angleDegrees,
            intakeAngle = intakeIO.pivotAngleDegrees,
            pieceDetected = feederIO.isBeamBroken,
            timestampMs = timestamp
        ))

        // ── 2. WRITE: Store → Hardware ──
        if (!isSimulation && swerveIO != null) {
            swerveIO.write(store.state.drive)
        }

        // Superstructure outputs
        flywheelIO.setVelocityRpm(store.state.superstructure.flywheel.targetVelocityRpm)
        cowlIO.setTargetAngle(store.state.superstructure.cowl.targetAngleDegrees)
        intakeIO.setPivotAngle(store.state.superstructure.intake.targetAngleDegrees)

        val targetRollerSpeed = store.state.superstructure.intake.targetRollerVelocityRps
        intakeIO.setRollerVoltage((targetRollerSpeed / 10.0) * 12.0)

        val targetFeederSpeed = store.state.superstructure.feeder.targetVelocityRps
        feederIO.setAppliedVoltage((targetFeederSpeed / 12.0) * 12.0)

        // ── 3. PUBLISH: Everything → NT4 + CSV ──
        publisher.publish(store.state, gamepad1, gamepad2)

        // Publish EKF covariance diagonals
        val cov = store.state.drive.poseEstimator.covariance
        dataLoggingTelemetry.putDoubleArray("Robot/Odometry/Covariance", doubleArrayOf(cov.m00, cov.m11, cov.m22))

        // Publish 3D robot pose (quaternion format for AdvantageScope)
        val heading = store.state.drive.odometryHeading
        val halfH = heading / 2.0
        dataLoggingTelemetry.putDoubleArray("Robot/Pose3d", doubleArrayOf(
            store.state.drive.odometryX, store.state.drive.odometryY, 0.0,
            Math.cos(halfH), 0.0, 0.0, Math.sin(halfH)
        ))

        // Publish swerve module states
        val vx = store.state.drive.xVelocityMetersPerSecond
        val vy = store.state.drive.yVelocityMetersPerSecond
        val omega = store.state.drive.angularVelocityRadiansPerSecond
        val offsets = arrayOf(
            Pair(0.35, 0.35), Pair(0.35, -0.35),
            Pair(-0.35, 0.35), Pair(-0.35, -0.35)
        )
        val swerveStates = DoubleArray(8)
        for (i in 0..3) {
            val wvx = vx - omega * offsets[i].second
            val wvy = vy + omega * offsets[i].first
            swerveStates[i * 2] = Math.atan2(wvy, wvx)
            swerveStates[i * 2 + 1] = Math.hypot(wvx, wvy)
        }
        dataLoggingTelemetry.putDoubleArray("Robot/SwerveStates", swerveStates)
    }

    /**
     * Gracefully shuts down background logging threads and telemetry.
     */
    fun close() {
        dataLoggingTelemetry.close()
    }
}
