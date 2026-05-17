package com.qualcomm.robotcore.hardware


interface HardwareMap {
    fun <T> get(classOrType: Class<out T>, deviceName: String): T
}

interface DcMotorSimple {
    enum class Direction { FORWARD, REVERSE }
    var direction: Direction
    var power: Double
}

interface CRServo : DcMotorSimple

interface DcMotorEx : DcMotor {
    val currentPosition: Int
    val velocity: Double
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

open class Telemetry {
    fun addData(key: String, value: Any) {}
    fun update() {}
}
open class ElapsedTime {
    private var startTime: Long = System.nanoTime()
    fun reset() { startTime = System.nanoTime() }
    fun seconds(): Double = (System.nanoTime() - startTime) / 1e9
}
