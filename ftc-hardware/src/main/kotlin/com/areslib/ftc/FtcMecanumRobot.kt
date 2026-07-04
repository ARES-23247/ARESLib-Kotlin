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
import com.areslib.subsystem.DriveSubsystem
import com.areslib.subsystem.MecanumDriveFacade

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

    // Subsystem Facades
    val drive = DriveSubsystem(store)
    val mecanumDrive = MecanumDriveFacade(store)

    // 1. Physical Hardware IO & Kinematics Controllers
    val mecanumIO = MecanumHardwareIO(
        hardwareMap, flName, frName, blName, brName,
        flDirection = flDirection,
        frDirection = frDirection,
        blDirection = blDirection,
        brDirection = brDirection
    )

    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    override fun updateHardwareInputs() {
        com.areslib.hardware.HardwareRegistry.refreshAll()
    }

    override fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double) {
        mecanumIO.drive(store.state.drive, kinematics, batteryVoltage, dtSeconds)
    }

    override fun publishRobotTelemetry(timestamp: Long) {
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

        // Detailed hardware analytics logging
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fl", mecanumIO.flIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fr", mecanumIO.frIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("bl", mecanumIO.blIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("br", mecanumIO.brIO)
    }

    override fun safeHardware() {
        com.areslib.hardware.HardwareRegistry.safeAll()
    }
}
