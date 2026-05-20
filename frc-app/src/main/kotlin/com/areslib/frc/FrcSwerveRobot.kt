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
import com.areslib.control.BrownoutGuard

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
    private val floorIO: com.areslib.hardware.FloorIO = object : com.areslib.hardware.FloorIO {
        override fun setAppliedVoltage(volts: Double) {}
    },
    private val climberIO: com.areslib.hardware.ClimberIO = object : com.areslib.hardware.ClimberIO {
        override fun setTargetExtension(meters: Double) {}
        override fun setAppliedVoltage(volts: Double) {}
    },
    private val isSimulation: Boolean = false,
    baseTelemetry: ITelemetry = FRCTelemetry()
) : AresRobot() {

    // Unified telemetry pipeline: base telemetry → CSV wrapper → publisher
    private val dataLoggingTelemetry = DataLoggingTelemetry(baseTelemetry)
    private val publisher = ARESNetworkStatePublisher(dataLoggingTelemetry)

    /** Direct access to the underlying telemetry for custom keys (3D viz, etc). */
    val telemetry: ITelemetry get() = dataLoggingTelemetry

    // Pre-allocated buffers to prevent high-frequency GC allocations in update loop
    private val covarianceDiagonals = DoubleArray(3)
    private val pose3dArray = DoubleArray(7)
    private val swerveStates = DoubleArray(8)

    /** Brownout protection guard — auto-scales motor power on voltage sag */
    val brownoutGuard = BrownoutGuard.frcDefaults()

    /** Battery voltage supplier — set this from the platform layer (e.g., RobotController.getBatteryVoltage()) */
    var batteryVoltageSupplier: () -> Double = { 12.6 }

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
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()

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
            floorVelocityRps = floorIO.velocityRps,
            climberExtensionMeters = climberIO.extensionMeters,
            timestampMs = timestamp
        ))

        // ── 2. WRITE: Store → Hardware ──
        if (!isSimulation && swerveIO != null) {
            swerveIO.write(store.state.drive)
        }

        // ── 3. BROWNOUT PROTECTION ──
        val batteryVoltage = batteryVoltageSupplier()
        brownoutGuard.update(batteryVoltage)

        // Apply power scaling to all superstructure outputs
        // (Drive swerve modules have their own voltage compensation via CTRE)
        val scale = brownoutGuard.powerScale
        flywheelIO.setVelocityRpm(store.state.superstructure.flywheel.targetVelocityRpm * scale)
        cowlIO.setTargetAngle(store.state.superstructure.cowl.targetAngleDegrees)

        val pivotAngle = store.state.superstructure.intake.targetAngleDegrees
        intakeIO.setPivotAngle(pivotAngle)

        val targetRollerSpeed = store.state.superstructure.intake.targetRollerVelocityRps
        intakeIO.setRollerVoltage((targetRollerSpeed / 10.0) * 12.0 * scale)

        val targetFeederSpeed = store.state.superstructure.feeder.targetVelocityRps
        feederIO.setAppliedVoltage((targetFeederSpeed / 12.0) * 12.0 * scale)

        val targetFloorSpeed = store.state.superstructure.floor.targetVelocityRps
        floorIO.setAppliedVoltage((targetFloorSpeed / 12.0) * 12.0 * scale)

        val targetClimberVoltage = store.state.superstructure.climber.targetVoltage
        climberIO.setAppliedVoltage(targetClimberVoltage * scale)

        // ── 4. PUBLISH: Everything → NT4 + CSV ──
        publisher.publish(store.state, gamepad1, gamepad2)

        // Publish brownout telemetry
        dataLoggingTelemetry.putNumber("Robot/BatteryVoltage", batteryVoltage)
        dataLoggingTelemetry.putNumber("Robot/BrownoutPowerScale", brownoutGuard.powerScale)
        dataLoggingTelemetry.putString("Robot/BrownoutState", brownoutGuard.state.name)
        dataLoggingTelemetry.putNumber("Robot/BatteryPercent", brownoutGuard.batteryPercent)

        // Publish EKF covariance diagonals
        val cov = store.state.drive.poseEstimator.covariance
        covarianceDiagonals[0] = cov.m00
        covarianceDiagonals[1] = cov.m11
        covarianceDiagonals[2] = cov.m22
        dataLoggingTelemetry.putDoubleArray("Robot/Odometry/Covariance", covarianceDiagonals)

        // Publish 3D robot pose (quaternion format for AdvantageScope)
        val heading = store.state.drive.odometryHeading
        val halfH = heading / 2.0
        pose3dArray[0] = store.state.drive.odometryX
        pose3dArray[1] = store.state.drive.odometryY
        pose3dArray[2] = 0.0
        pose3dArray[3] = Math.cos(halfH)
        pose3dArray[4] = 0.0
        pose3dArray[5] = 0.0
        pose3dArray[6] = Math.sin(halfH)
        dataLoggingTelemetry.putDoubleArray("Robot/Pose3d", pose3dArray)

        // Publish swerve module states
        val vx = store.state.drive.xVelocityMetersPerSecond
        val vy = store.state.drive.yVelocityMetersPerSecond
        val omega = store.state.drive.angularVelocityRadiansPerSecond
        for (i in 0..3) {
            val wvx = vx - omega * SWERVE_OFFSETS[i].second
            val wvy = vy + omega * SWERVE_OFFSETS[i].first
            swerveStates[i * 2] = Math.atan2(wvy, wvx)
            swerveStates[i * 2 + 1] = Math.hypot(wvx, wvy)
        }
        dataLoggingTelemetry.putDoubleArray("Robot/SwerveStates", swerveStates)
    }

    companion object {
        private val SWERVE_OFFSETS = arrayOf(
            Pair(0.35, 0.35), Pair(0.35, -0.35),
            Pair(-0.35, 0.35), Pair(-0.35, -0.35)
        )
    }

    /**
     * Gracefully shuts down background logging threads and telemetry.
     */
    fun close() {
        dataLoggingTelemetry.close()
    }
}
