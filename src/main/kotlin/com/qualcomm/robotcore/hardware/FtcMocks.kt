package com.qualcomm.robotcore.hardware

interface HardwareMap {
    fun <T> get(classOrType: Class<out T>, deviceName: String): T
}

interface DcMotorEx {
    val currentPosition: Int
    val velocity: Double
    fun setPower(power: Double)
}

interface AnalogInput {
    val voltage: Double
}

open class Gamepad {
    var left_stick_x: Float = 0f
    var left_stick_y: Float = 0f
    var right_stick_x: Float = 0f
    var right_stick_y: Float = 0f
}

abstract class LinearOpMode {
    abstract fun runOpMode()
    val hardwareMap: HardwareMap = object : HardwareMap {
        override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
            throw NotImplementedError()
        }
    }
    fun opModeIsActive(): Boolean = true
}
