package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.areslib.kinematics.MecanumWheelSpeeds

class MecanumHardwareIO(
    hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br"
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
     * @param speeds The wheel speeds (assumed -1.0 to 1.0 normalized range).
     * @param batteryVolts The current battery voltage to compensate for sag.
     */
    fun apply(speeds: MecanumWheelSpeeds, batteryVolts: Double = 12.0) {
        val maxVolts = 12.0
        val actualVolts = if (batteryVolts > 0.1) batteryVolts else 12.0
        
        frontLeft.power = (speeds.frontLeftMetersPerSecond * maxVolts) / actualVolts
        frontRight.power = (speeds.frontRightMetersPerSecond * maxVolts) / actualVolts
        backLeft.power = (speeds.backLeftMetersPerSecond * maxVolts) / actualVolts
        backRight.power = (speeds.backRightMetersPerSecond * maxVolts) / actualVolts
    }
}
