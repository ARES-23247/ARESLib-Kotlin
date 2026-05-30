package com.qualcomm.robotcore.eventloop.opmode

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.Gamepad
import com.qualcomm.robotcore.hardware.Telemetry

annotation class TeleOp(val name: String = "", val group: String = "")
annotation class Autonomous(val name: String = "", val group: String = "")

abstract class LinearOpMode {
    abstract fun runOpMode()
    val hardwareMap: HardwareMap = object : HardwareMap() {
        override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
            throw NotImplementedError()
        }
        override fun <T> getAll(classOrType: Class<out T>): List<T> {
            return emptyList()
        }
    }
    val gamepad1 = Gamepad()
    val telemetry = Telemetry()
    fun opModeIsActive(): Boolean = true
    fun waitForStart() {}
}
