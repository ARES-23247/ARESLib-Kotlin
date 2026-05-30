package com.qualcomm.robotcore.hardware


open class HardwareMap {
    open fun <T> get(classOrType: Class<out T>, deviceName: String): T {
        throw NotImplementedError("Mock HardwareMap.get() not overridden")
    }
    open fun <T> getAll(classOrType: Class<out T>): List<T> {
        throw NotImplementedError("Mock HardwareMap.getAll() not overridden")
    }
}

interface VoltageSensor {
    val voltage: Double
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
    fun getCurrent(unit: org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit): Double
}

open class AnalogInput {
    open val voltage: Double = 0.0
}

open class Gamepad {
    @JvmField var left_stick_x: Float = 0f
    @JvmField var left_stick_y: Float = 0f
    @JvmField var right_stick_x: Float = 0f
    @JvmField var right_stick_y: Float = 0f

    @JvmField var left_trigger: Float = 0f
    @JvmField var right_trigger: Float = 0f

    @JvmField var a: Boolean = false
    @JvmField var b: Boolean = false
    @JvmField var x: Boolean = false
    @JvmField var y: Boolean = false

    @JvmField var dpad_up: Boolean = false
    @JvmField var dpad_down: Boolean = false
    @JvmField var dpad_left: Boolean = false
    @JvmField var dpad_right: Boolean = false

    @JvmField var left_bumper: Boolean = false
    @JvmField var right_bumper: Boolean = false

    @JvmField var start: Boolean = false
    @JvmField var back: Boolean = false
    @JvmField var guide: Boolean = false

    @JvmField var left_stick_button: Boolean = false
    @JvmField var right_stick_button: Boolean = false
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

interface DistanceSensor {
    fun getDistance(unit: org.firstinspires.ftc.robotcore.external.navigation.Distance): Double
}

interface ColorSensor {
    fun red(): Int
    fun green(): Int
    fun blue(): Int
    fun alpha(): Int
}

class NormalizedRGBA {
    @JvmField var red: Float = 0f
    @JvmField var green: Float = 0f
    @JvmField var blue: Float = 0f
    @JvmField var alpha: Float = 0f
}

interface NormalizedColorSensor {
    val normalizedColors: NormalizedRGBA
}

interface IMU {
    class Parameters(val hubOrientationOnRobot: com.qualcomm.hardware.rev.RevHubOrientationOnRobot)

    fun initialize(parameters: Parameters): Boolean
    fun resetYaw()
    fun getRobotYawPitchRollAngles(): org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles
    fun getRobotAngularVelocity(unit: org.firstinspires.ftc.robotcore.external.navigation.AngleUnit): org.firstinspires.ftc.robotcore.external.navigation.AngularVelocity
}


