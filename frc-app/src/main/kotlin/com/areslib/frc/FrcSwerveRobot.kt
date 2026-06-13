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
import com.areslib.telemetry.RobotStatusTracker
import com.areslib.telemetry.RobotWebServer
import com.areslib.control.BrownoutGuard
import com.areslib.frc.action.*
import com.areslib.frc.subsystem.*
import com.areslib.frc.state.marvinXIX
import com.areslib.frc.telemetry.MarvinStatePublisher

import com.ctre.phoenix6.swerve.SwerveDrivetrain

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
    private val swerveIO: FRCSwerveHardwareIO? = null,
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
    private val visionIO: com.areslib.hardware.vision.VisionIO? = null,
    private val isSimulation: Boolean = false,
    baseTelemetry: ITelemetry = FRCTelemetry()
) : AresRobot(
    initialState = com.areslib.state.RobotState(
        vision = com.areslib.state.VisionState(
            filterConfig = com.areslib.hardware.vision.VisionFilterConfig.frcDefaults()
        )
    ),
    reducer = com.areslib.frc.reducer.MarvinReducer::reduce
) {

    val marvinShooter = MarvinShooterSubsystem(store)
    val marvinIntake = MarvinIntakeSubsystem(store)
    val marvinClimber = MarvinClimberSubsystem(store)

    private val visionInputs = com.areslib.hardware.vision.VisionIOInputs()

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

    init {
        RobotWebServer.start()
        RobotStatusTracker.isEnabled = false
        RobotStatusTracker.activeOpMode = "Init"
    }

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
        // Refresh all hardware status signals from CAN bus in a batch
        swerveIO?.refresh()
        flywheelIO.refresh()
        cowlIO.refresh()
        intakeIO.refresh()
        feederIO.refresh()
        floorIO.refresh()
        climberIO.refresh()

        val isEnabled = try {
            edu.wpi.first.wpilibj.DriverStation.isEnabled()
        } catch (_: Exception) {
            false
        }

        val mode = try {
            when {
                edu.wpi.first.wpilibj.DriverStation.isAutonomous() -> "Auto"
                edu.wpi.first.wpilibj.DriverStation.isTeleop() -> "Teleop"
                edu.wpi.first.wpilibj.DriverStation.isTest() -> "Test"
                else -> "Disabled"
            }
        } catch (_: Exception) {
            "Active"
        }

        if (isEnabled) {
            RobotWebServer.stop()
        } else {
            RobotWebServer.start()
        }

        RobotStatusTracker.isEnabled = isEnabled
        RobotStatusTracker.activeOpMode = mode

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

        // ── 1.5. READ: Vision → Store ──
        visionIO?.let { io ->
            io.updateInputs(visionInputs)
            if (visionInputs.measurements.isNotEmpty()) {
                val measurement = visionInputs.measurements[0]
                if (!isSimulation && swerveIO != null) {
                    val wpiPose = edu.wpi.first.math.geometry.Pose2d(
                        measurement.targetPose.translation.x,
                        measurement.targetPose.translation.y,
                        edu.wpi.first.math.geometry.Rotation2d.fromRadians(measurement.targetPose.rotation.z)
                    )
                    val latencyMs = timestamp - measurement.timestampMs
                    val timestampSec = edu.wpi.first.wpilibj.Timer.getFPGATimestamp() - (latencyMs / 1000.0)
                    try {
                        swerveIO.addVisionMeasurement(wpiPose, timestampSec)
                    } catch (e: Exception) {
                        System.err.println("FrcSwerveRobot: Failed to feed vision to SwerveDrivetrain: ${e.message}")
                    }
                }
                store.dispatch(RobotAction.VisionMeasurementsReceived(
                    visionInputs.measurements,
                    timestamp,
                    null
                ))
            }
            RobotStatusTracker.visionConnected = visionInputs.isConnected
        } ?: run {
            RobotStatusTracker.visionConnected = false
        }

        // Read superstructure sensors
        store.dispatch(SuperstructureSensorUpdate(
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
        val marvin = store.state.superstructure.marvinXIX
        flywheelIO.setVelocityRpm(marvin.flywheel.targetVelocityRpm * scale)
        cowlIO.setTargetAngle(marvin.cowl.targetAngleDegrees)

        val pivotAngle = marvin.intake.targetAngleDegrees
        intakeIO.setPivotAngle(pivotAngle)

        val targetRollerSpeed = marvin.intake.targetRollerVelocityRps
        intakeIO.setRollerVoltage((targetRollerSpeed / 10.0) * 12.0 * scale)

        val targetFeederSpeed = marvin.feeder.targetVelocityRps
        feederIO.setAppliedVoltage((targetFeederSpeed / 12.0) * 12.0 * scale)

        val targetFloorSpeed = marvin.floor.targetVelocityRps
        floorIO.setAppliedVoltage((targetFloorSpeed / 12.0) * 12.0 * scale)

        val targetClimberVoltage = marvin.climber.targetVoltage
        climberIO.setAppliedVoltage(targetClimberVoltage * scale)

        // ── 4. PUBLISH: Everything → NT4 + CSV ──
        publisher.publish(store.state, gamepad1, gamepad2)

        // Publish Marvin XIX specific sub-states
        MarvinStatePublisher.publish(store.state, dataLoggingTelemetry)

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

        // Publish superstructure diagnostics
        dataLoggingTelemetry.putNumber("Diagnostics/Flywheel/CurrentAmps", flywheelIO.currentAmps)
        dataLoggingTelemetry.putNumber("Diagnostics/Flywheel/TempCelsius", flywheelIO.tempCelsius)
        dataLoggingTelemetry.putNumber("Diagnostics/Cowl/CurrentAmps", cowlIO.currentAmps)
        dataLoggingTelemetry.putNumber("Diagnostics/Intake/PivotCurrentAmps", intakeIO.pivotCurrentAmps)
        dataLoggingTelemetry.putNumber("Diagnostics/Intake/RollerCurrentAmps", intakeIO.rollerCurrentAmps)
        dataLoggingTelemetry.putNumber("Diagnostics/Feeder/CurrentAmps", feederIO.currentAmps)
        dataLoggingTelemetry.putNumber("Diagnostics/Floor/CurrentAmps", floorIO.currentAmps)
        dataLoggingTelemetry.putNumber("Diagnostics/Climber/CurrentAmps", climberIO.currentAmps)

        // Publish swerve diagnostics
        if (swerveIO != null) {
            dataLoggingTelemetry.putDoubleArray("Diagnostics/Swerve/Currents", swerveIO.currents)
            dataLoggingTelemetry.putDoubleArray("Diagnostics/Swerve/EncoderPositions", swerveIO.encoderPositions)
        }
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
        RobotStatusTracker.isEnabled = false
        RobotWebServer.stop()
        dataLoggingTelemetry.close()
    }
}
