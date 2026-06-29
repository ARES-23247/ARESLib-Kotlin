package com.qualcomm.robotcore.eventloop.opmode

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.Gamepad
import org.firstinspires.ftc.robotcore.external.Telemetry

annotation class TeleOp(val name: String = "", val group: String = "")
annotation class Autonomous(val name: String = "", val group: String = "")

abstract class LinearOpMode {
    abstract fun runOpMode()
    var hardwareMap: HardwareMap = object : HardwareMap() {
        override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
            throw NotImplementedError()
        }
        override fun <T> getAll(classOrType: Class<out T>): List<T> {
            return emptyList()
        }
    }
    val gamepad1 = Gamepad()
    val gamepad2 = Gamepad()
    val telemetry: Telemetry = org.firstinspires.ftc.robotcore.external.MockTelemetry()
    
    @Volatile var isStarted = false
    @Volatile var isStopRequested = false
    
    fun opModeIsActive(): Boolean = isStarted && !isStopRequested
    fun opModeInInit(): Boolean = !isStarted && !isStopRequested
    
    fun waitForStart() {
        while (!isStarted && !isStopRequested) {
            Thread.sleep(10)
        }
    }
    
    fun sleep(milliseconds: Long) {
        Thread.sleep(milliseconds)
    }
}
