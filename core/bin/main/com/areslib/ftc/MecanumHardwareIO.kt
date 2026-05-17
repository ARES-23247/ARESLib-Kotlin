package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.areslib.kinematics.MecanumWheelSpeeds

class MecanumHardwareIO(hardwareMap: HardwareMap) {
    private val frontLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, "fl")
    private val frontRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, "fr")
    private val backLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, "bl")
    private val backRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, "br")

    init {
        // Typical mecanum configuration requires right side to be reversed
        frontLeft.direction = DcMotorSimple.Direction.FORWARD
        backLeft.direction = DcMotorSimple.Direction.FORWARD
        frontRight.direction = DcMotorSimple.Direction.REVERSE
        backRight.direction = DcMotorSimple.Direction.REVERSE
    }

    /**
     * Applies the calculated wheel speeds to the physical motors.
     * @param speeds The normalized wheel speeds (assumed -1.0 to 1.0 range).
     */
    fun apply(speeds: MecanumWheelSpeeds) {
        frontLeft.power = speeds.frontLeftMetersPerSecond
        frontRight.power = speeds.frontRightMetersPerSecond
        backLeft.power = speeds.backLeftMetersPerSecond
        backRight.power = speeds.backRightMetersPerSecond
    }
}
