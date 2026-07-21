package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry

/**
 * A builder class implementing a fluent Kotlin DSL for configuring an [FtcMecanumRobot] instance.
 */
class FtcMecanumRobotBuilder(private val hardwareMap: HardwareMap) {
    /**
     * Hardware map name for the Front Left drive motor. Defaults to `"fl"`.
     */
    var frontLeftMotorName: String = "fl"
    
    /**
     * Hardware map name for the Front Right drive motor. Defaults to `"fr"`.
     */
    var frontRightMotorName: String = "fr"
    
    /**
     * Hardware map name for the Rear Left drive motor. Defaults to `"rl"`.
     */
    var rearLeftMotorName: String = "rl"
    
    /**
     * Hardware map name for the Rear Right drive motor. Defaults to `"rr"`.
     */
    var rearRightMotorName: String = "rr"
    
    /**
     * Hardware map name for the Pinpoint odometry computer. Defaults to `"pinpoint"`. Can be null if no Pinpoint is used.
     */
    var pinpointName: String? = "pinpoint"
    
    /**
     * Hardware map name for the Limelight camera. Defaults to `"limelight"`. Can be null if no Limelight is used.
     */
    var limelightName: String? = "limelight"
    
    /**
     * Optional local telemetry channel (e.g. FTC dashboard or driver station telemetry).
     */
    var telemetry: Telemetry? = null


    /**
     * Motor direction for the Front Left drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD].
     */
    var frontLeftMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD

    /**
     * Motor direction for the Front Right drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE].
     */
    var frontRightMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE

    /**
     * Motor direction for the Rear Left drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD].
     */
    var rearLeftMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD

    /**
     * Motor direction for the Rear Right drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE].
     */
    var rearRightMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE

    /**
     * Constructs and returns the fully configured [FtcMecanumRobot] instance.
     */
    fun build(): FtcMecanumRobot = FtcMecanumRobot(
        hardwareMap = hardwareMap,
        flName = frontLeftMotorName,
        frName = frontRightMotorName,
        rlName = rearLeftMotorName,
        rrName = rearRightMotorName,
        pinpointName = pinpointName,
        limelightName = limelightName,
        localTelemetry = telemetry,
        flDirection = frontLeftMotorDirection,
        frDirection = frontRightMotorDirection,
        rlDirection = rearLeftMotorDirection,
        rrDirection = rearRightMotorDirection
    )
}

/**
 * Creates and configures an [FtcMecanumRobot] instance using a clean, fluent Kotlin DSL.
 *
 * Example usage:
 * ```kotlin
 * val robot = ftcMecanumRobot(hardwareMap) {
 *     frontLeftMotorName = "fl_motor"
 *     frontRightMotorName = "fr_motor"
 *     backLeftMotorName = "bl_motor"
 *     backRightMotorName = "br_motor"
 *     pinpointName = "odo"
 *     limelightName = "limelight_cam"
 * }
 * ```
 */
fun ftcMecanumRobot(
    hardwareMap: HardwareMap,
    block: FtcMecanumRobotBuilder.() -> Unit
): FtcMecanumRobot = FtcMecanumRobotBuilder(hardwareMap).apply(block).build()
