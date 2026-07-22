package com.areslib.ftc.core

import com.qualcomm.robotcore.hardware.HardwareMap
import com.areslib.ftc.hardware.FtcHardwareMapInitializer
import com.areslib.ftc.drivetrain.PinpointIO
import com.areslib.hardware.sensor.ImuIO
import com.areslib.hardware.vision.VisionIO

/**
 * Class implementation for Ftc Hardware Initializer.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class FtcHardwareInitializer(
    private val hardwareMap: HardwareMap,
    private val pinpointName: String? = "pinpoint",
    private val limelightName: String? = "limelight",
    private val imuName: String? = "imu",
    private val pinpointXOffsetMm: Double = 0.0,
    private val pinpointYOffsetMm: Double = 0.0,
    private val pinpointEncoderResolution: Double? = null,
    private val pinpointXDirection: com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection = com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection.FORWARD,
    private val pinpointYDirection: com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection = com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection.FORWARD,
    private val pinpointIsCcwPositive: Boolean = false
) {
    val pinpointIO: PinpointIO? by lazy {
        FtcHardwareMapInitializer.initPinpoint(
            hardwareMap = hardwareMap,
            pinpointName = pinpointName,
            xOffsetMm = pinpointXOffsetMm,
            yOffsetMm = pinpointYOffsetMm,
            encoderResolution = pinpointEncoderResolution,
            xDirection = pinpointXDirection,
            yDirection = pinpointYDirection,
            isCcwPositive = pinpointIsCcwPositive
        )
    }

    val imuIO: ImuIO? by lazy {
        FtcHardwareMapInitializer.initImu(hardwareMap, imuName)
    }

    val limelightIO: VisionIO? by lazy {
        FtcHardwareMapInitializer.initLimelight(hardwareMap, limelightName)
    }

    /**
     * close declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun close() {
        pinpointIO?.close()
        try {
            (imuIO as? AutoCloseable)?.close()
        } catch (_: Exception) {}
        try {
            (limelightIO as? AutoCloseable)?.close()
        } catch (_: Exception) {}
        com.areslib.hardware.HardwareRegistry.closeAll()
        com.areslib.ftc.hardware.FtcMotor.unregisterAll()
    }
}
