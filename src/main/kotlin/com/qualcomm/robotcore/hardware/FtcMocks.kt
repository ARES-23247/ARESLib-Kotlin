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

abstract class LinearOpMode {
    abstract fun runOpMode()
    val hardwareMap: HardwareMap = object : HardwareMap {
        override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
            throw NotImplementedError()
        }
    }
    fun opModeIsActive(): Boolean = true
}
