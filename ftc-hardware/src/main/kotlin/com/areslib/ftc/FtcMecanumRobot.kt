package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.ftc.MecanumHardwareIO
import com.areslib.kinematics.MecanumKinematics
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.math.ChassisSpeeds
import com.areslib.telemetry.logDriveMotor

/**
 * Concrete Mecanum Drive robot facade.
 * Implements wheel kinematics conversions, battery sag motor compensation,
 * and publishes mecanum-specific motor currents and powers.
 */
class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br",
    pinpointName: String? = "pinpoint",
    limelightName: String? = "limelight",
    localTelemetry: Telemetry? = null,
    flDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    frDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
    blDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    brDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE
) : FtcBaseRobot(hardwareMap, pinpointName, limelightName, localTelemetry) {

    // 1. Physical Hardware IO & Kinematics Controllers
    val mecanumIO = MecanumHardwareIO(
        hardwareMap, flName, frName, blName, brName,
        flDirection = flDirection,
        frDirection = frDirection,
        blDirection = blDirection,
        brDirection = brDirection
    )

    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    init {
        // Register drive motors with the power manager for current budgeting
        powerManager.registerMotors(listOf(mecanumIO.flIO, mecanumIO.frIO, mecanumIO.blIO, mecanumIO.brIO))
    }

    override fun updateHardwareInputs() {
        // Update cached inputs for drivetrain motors from bulk read registers
        mecanumIO.updateInputs()
    }

    override fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double) {
        // Process kinematics using current State targets
        val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
        val vx = store.state.drive.xVelocityMetersPerSecond * maxSpeed
        val vy = store.state.drive.yVelocityMetersPerSecond * maxSpeed
        val omega = store.state.drive.angularVelocityRadiansPerSecond * maxSpeed
        
        // Field-centric vs Robot-centric coordinate transformation
        val chassisSpeeds = if (store.state.drive.isFieldCentric) {
            ChassisSpeeds.fromFieldRelativeSpeeds(
                vx, vy, omega,
                Rotation2d(store.state.drive.poseEstimator.estimatedPose.heading.radians)
            )
        } else {
            ChassisSpeeds(vx, vy, omega)
        }
        val wheelSpeeds = kinematics.toWheelSpeeds(chassisSpeeds)

        // Apply battery-compensated voltage vectors with power scaling exactly once
        mecanumIO.apply(
            speeds = wheelSpeeds.normalize(mecanumIO.maxWheelSpeedMetersPerSecond),
            batteryVolts = batteryVoltage,
            dtSeconds = dtSeconds,
            powerScale = powerScale
        )
        // Update local estimation tracking power scale
        mecanumIO.applyPowerScale(powerScale)
    }

    override fun publishRobotTelemetry(timestamp: Long) {
        telemetryManager.dataLoggingTelemetry.putNumber("motor_lf_current", mecanumIO.flIO.currentAmps)
        telemetryManager.dataLoggingTelemetry.putNumber("motor_rf_current", mecanumIO.frIO.currentAmps)
        telemetryManager.dataLoggingTelemetry.putNumber("motor_lr_current", mecanumIO.blIO.currentAmps)
        telemetryManager.dataLoggingTelemetry.putNumber("motor_rr_current", mecanumIO.brIO.currentAmps)

        // Publish physical motor telemetry (power, encoder positions, velocities, currents)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("FL", mecanumIO.flIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("FR", mecanumIO.frIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("BL", mecanumIO.blIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("BR", mecanumIO.brIO)

        // Local Driver Station telemetry specific prints
        localTelemetry?.let { t ->
            t.addData("Motor Powers", String.format("FL:%.2f | FR:%.2f | RL:%.2f | RR:%.2f",
                mecanumIO.flIO.power * mecanumIO.flIO.powerScale,
                mecanumIO.frIO.power * mecanumIO.frIO.powerScale,
                mecanumIO.blIO.power * mecanumIO.blIO.powerScale,
                mecanumIO.brIO.power * mecanumIO.brIO.powerScale
            ))

            val currentStr = if (powerManager.floodgate != null) {
                String.format("%.1f A (Physical)", powerManager.floodgate.current)
            } else {
                String.format("%.1f A (Estimated)", powerManager.currentAmps)
            }
            t.addData("Current Draw", currentStr)
        }
    }

    override fun safeHardware() {
        try {
            mecanumIO.apply(
                speeds = MecanumWheelSpeeds(0.0, 0.0, 0.0, 0.0),
                batteryVolts = 12.0,
                dtSeconds = 0.02,
                powerScale = 0.0
            )
        } catch (_: Throwable) {}
    }
}
