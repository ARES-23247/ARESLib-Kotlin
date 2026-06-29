package com.areslib.sim

import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.VoltageSensor
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.hardware.limelightvision.LLResult
import com.qualcomm.hardware.limelightvision.LLResultTypes
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D
import org.firstinspires.ftc.robotcore.external.navigation.Position
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit
import com.qualcomm.robotcore.hardware.HardwareMap
import kotlin.math.abs

class SimDcMotorEx : DcMotorEx {
    override var direction: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD
    override var mode: DcMotor.RunMode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
    override var power: Double = 0.0
    
    override var currentPosition: Int = 0
    override var velocity: Double = 0.0

    override fun getCurrent(unit: CurrentUnit): Double {
        // Return simulated current draw: 0.15A idle, scaling up to 4.2A under load
        return abs(power) * 4.05 + 0.15
    }
}

class SimLimelight3A : Limelight3A() {
    @Volatile
    private var result: LLResult? = null

    fun setLatestResult(res: LLResult?) {
        this.result = res
    }

    override fun getLatestResult(): LLResult? = result
}

class SimLLResult(
    private val valid: Boolean,
    private val fiducials: List<LLResultTypes.FiducialResult>
) : LLResult() {
    override fun isValid(): Boolean = valid
    override fun getFiducialResults(): List<LLResultTypes.FiducialResult> = fiducials
}

class MecanumRobotDouble {
    val fl = SimDcMotorEx()
    val fr = SimDcMotorEx()
    val bl = SimDcMotorEx()
    val br = SimDcMotorEx()
    
    val pinpoint = GoBildaPinpointDriver()
    val limelight = SimLimelight3A()
    
    val voltageSensor = object : VoltageSensor {
        override val voltage: Double = 12.8
    }

    val hardwareMap = object : HardwareMap() {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
            return when (deviceName) {
                "fl" -> fl as T
                "fr" -> fr as T
                "rl", "bl" -> bl as T
                "rr", "br" -> br as T
                "pinpoint" -> pinpoint as T
                "limelight" -> limelight as T
                else -> throw IllegalArgumentException("Unknown simulated device name: $deviceName")
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getAll(classOrType: Class<out T>): List<T> {
            if (classOrType == VoltageSensor::class.java) {
                return listOf(voltageSensor as T)
            }
            return emptyList()
        }
    }

    // Encoder properties
    private val encoderTicksPerMeter = 2000.0 // Ticks per meter of wheel travel

    fun updateSensors(dt: Double, actualVx: Double, actualVy: Double, actualOmega: Double, trueX: Double, trueY: Double, trueHeadingRad: Double) {
        // 1. Update motor encoder positions based on simulated robot speed
        // FL = vx - vy - omega * (trackWidth + wheelBase)/2
        // FR = vx + vy + omega * (trackWidth + wheelBase)/2
        // BL = vx + vy - omega * (trackWidth + wheelBase)/2
        // BR = vx - vy + omega * (trackWidth + wheelBase)/2
        val L = 0.45 // trackWidth + wheelBase
        val wFL = actualVx - actualVy - actualOmega * L
        val wFR = actualVx + actualVy + actualOmega * L
        val wBL = actualVx + actualVy - actualOmega * L
        val wBR = actualVx - actualVy + actualOmega * L

        // Update wheel positions
        fl.currentPosition += (wFL * encoderTicksPerMeter * dt).toInt()
        fr.currentPosition += (wFR * encoderTicksPerMeter * dt).toInt()
        bl.currentPosition += (wBL * encoderTicksPerMeter * dt).toInt()
        br.currentPosition += (wBR * encoderTicksPerMeter * dt).toInt()

        fl.velocity = wFL * encoderTicksPerMeter
        fr.velocity = wFR * encoderTicksPerMeter
        bl.velocity = wBL * encoderTicksPerMeter
        br.velocity = wBR * encoderTicksPerMeter

        // 2. Feed simulated EKF/Pinpoint sensor coordinates
        pinpoint.posX = trueX
        pinpoint.posY = trueY
        pinpoint.heading = trueHeadingRad
    }
}
