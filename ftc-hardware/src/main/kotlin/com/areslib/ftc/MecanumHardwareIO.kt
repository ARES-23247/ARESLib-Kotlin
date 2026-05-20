package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.areslib.kinematics.MecanumWheelSpeeds

class MecanumHardwareIO @kotlin.jvm.JvmOverloads constructor(
    hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br",
    private val maxWheelSpeedMetersPerSecond: Double = 3.5
) {
    private val frontLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, flName)
    private val frontRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, frName)
    private val backLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, blName)
    private val backRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, brName)

    init {
        // Typical mecanum configuration requires right side to be reversed
        frontLeft.direction = DcMotorSimple.Direction.FORWARD
        backLeft.direction = DcMotorSimple.Direction.FORWARD
        frontRight.direction = DcMotorSimple.Direction.REVERSE
        backRight.direction = DcMotorSimple.Direction.REVERSE
    }

    /**
     * Applies the calculated wheel speeds to the physical motors using voltage compensation.
     * @param speeds The wheel speeds (in meters per second).
     * @param batteryVolts The current battery voltage to compensate for sag.
     */
    fun apply(speeds: MecanumWheelSpeeds, batteryVolts: Double = 12.0) {
        val maxVolts = 12.0
        val actualVolts = if (batteryVolts > 0.1) batteryVolts else 12.0
        val voltageCompensationFactor = maxVolts / actualVolts
        
        // Normalize meters per second speed into [-1.0, 1.0] range and apply compensation
        frontLeft.power = ((speeds.frontLeftMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)
        frontRight.power = ((speeds.frontRightMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)
        backLeft.power = ((speeds.backLeftMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)
        backRight.power = ((speeds.backRightMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)
    }

    /**
     * Applies a global power scale factor to all 4 drive motors.
     * Used by the BrownoutGuard to reduce output during voltage sag.
     * @param scale Power multiplier (0.0 = disabled, 1.0 = full power)
     */
    fun applyPowerScale(scale: Double) {
        val s = scale.coerceIn(0.0, 1.0)
        frontLeft.power = frontLeft.power * s
        frontRight.power = frontRight.power * s
        backLeft.power = backLeft.power * s
        backRight.power = backRight.power * s
    }
}
