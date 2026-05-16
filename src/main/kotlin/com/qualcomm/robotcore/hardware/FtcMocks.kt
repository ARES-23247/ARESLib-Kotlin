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

open class GoBildaPinpointDriver {
    var posX: Double = 0.0
    var posY: Double = 0.0
    var heading: Double = 0.0
    
    fun update() {}
}

open class Canvas {
    fun drawCircle(x: Double, y: Double, radius: Double) {}
    fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double) {}
    fun setStroke(color: String) {}
}

open class TelemetryPacket {
    val fieldOverlay = Canvas()
    fun put(key: String, value: Any) {}
}

object FtcDashboard {
    fun getInstance(): FtcDashboard = this
    fun sendTelemetryPacket(packet: TelemetryPacket) {}
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
