package com.qualcomm.robotcore.hardware

annotation class Autonomous(val name: String = "", val group: String = "")

interface HardwareMap {
    fun <T> get(classOrType: Class<out T>, deviceName: String): T
}

interface DcMotorSimple {
    enum class Direction { FORWARD, REVERSE }
    var direction: Direction
    fun setPower(power: Double)
}

interface DcMotorEx : DcMotorSimple {
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

abstract class LinearOpMode {
    abstract fun runOpMode()
    val hardwareMap: HardwareMap = object : HardwareMap {
        override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
            throw NotImplementedError()
        }
    }
    val gamepad1 = Gamepad()
    val telemetry = Telemetry()
    fun opModeIsActive(): Boolean = true
    fun waitForStart() {}
}

open class ElapsedTime {
    private var startTime: Long = System.nanoTime()
    fun reset() { startTime = System.nanoTime() }
    fun seconds(): Double = (System.nanoTime() - startTime) / 1e9
}
