package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap

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
     * Hardware map name for the Back Left drive motor. Defaults to `"bl"`.
     */
    var backLeftMotorName: String = "bl"
    
    /**
     * Hardware map name for the Back Right drive motor. Defaults to `"br"`.
     */
    var backRightMotorName: String = "br"
    
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
    var telemetry: Any? = null

    /**
     * Motor direction for the Front Left drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD].
     */
    var frontLeftMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD

    /**
     * Motor direction for the Front Right drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE].
     */
    var frontRightMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE

    /**
     * Motor direction for the Back Left drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD].
     */
    var backLeftMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD

    /**
     * Motor direction for the Back Right drive motor. Defaults to [com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE].
     */
    var backRightMotorDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE

    /**
     * Constructs and returns the fully configured [FtcMecanumRobot] instance.
     */
    fun build(): FtcMecanumRobot {
        return FtcMecanumRobot(
            hardwareMap = hardwareMap,
            flName = frontLeftMotorName,
            frName = frontRightMotorName,
            blName = backLeftMotorName,
            brName = backRightMotorName,
            pinpointName = pinpointName,
            limelightName = limelightName,
            localTelemetry = telemetry,
            flDirection = frontLeftMotorDirection,
            frDirection = frontRightMotorDirection,
            blDirection = backLeftMotorDirection,
            brDirection = backRightMotorDirection
        )
    }
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
): FtcMecanumRobot {
    val builder = FtcMecanumRobotBuilder(hardwareMap)
    builder.block()
    return builder.build()
}
