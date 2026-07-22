package com.areslib.sim.model

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

/**
 * Class implementation for Sim Dc Motor Ex.
 *
 * Robotics framework control component.
 */
class SimDcMotorEx : DcMotorEx {
    override var direction: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD
    @Volatile override var mode: DcMotor.RunMode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
    @Volatile override var power: Double = 0.0
    
    @Volatile override var currentPosition: Int = 0
    @Volatile override var velocity: Double = 0.0

    override fun getCurrent(unit: CurrentUnit): Double {
        // Return simulated current draw: 0.15A idle, scaling up to 4.2A under load
        return abs(power) * 4.05 + 0.15
    }
}

/**
 * Class implementation for Sim Servo.
 *
 * Robotics framework control component.
 */
class SimServo : com.qualcomm.robotcore.hardware.Servo {
    override var position: Double = 0.0
}

/**
 * Class implementation for Sim Limelight3 A.
 *
 * Robotics framework control component.
 */
class SimLimelight3A : Limelight3A() {
    @Volatile
    private var result: LLResult? = null

    fun setLatestResult(res: LLResult?) {
        this.result = res
    }

    override fun getLatestResult(): LLResult? = result
}

/**
 * Class implementation for Sim L L Result.
 *
 * Robotics framework control component.
 */
class SimLLResult(
    private val valid: Boolean,
    private val fiducials: List<LLResultTypes.FiducialResult>,
    private val botpose: Pose3D? = null
) : LLResult() {
    override fun isValid(): Boolean = valid
    override fun getFiducialResults(): List<LLResultTypes.FiducialResult> = fiducials
    override fun getBotpose(): Pose3D? = botpose
}

/**
 * Class implementation for Mecanum Robot Double.
 *
 * Robotics framework control component.
 */
class MecanumRobotDouble {
    val fl = SimDcMotorEx()
    val fr = SimDcMotorEx()
    val rl = SimDcMotorEx()
    val rr = SimDcMotorEx()
    
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
                "rl", "bl" -> rl as T
                "rr", "br" -> rr as T
                "pinpoint" -> pinpoint as T
                "limelight" -> limelight as T
                else -> {
                    if (com.qualcomm.robotcore.hardware.Servo::class.java.isAssignableFrom(classOrType)) {
                        println("[SimHardwareMap] Unknown device '$deviceName' requested. Returning default SimServo.")
                        SimServo() as T
                    } else {
                        println("[SimHardwareMap] Unknown device '$deviceName' requested. Returning default SimDcMotorEx.")
                        SimDcMotorEx() as T
                    }
                }
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

    fun updateSensors(dt: Double, actualVx: Double, actualVy: Double, actualOmega: Double, trueX: Double, trueY: Double, trueHeadingRad: Double, isPinpointCcwPositive: Boolean = true) {
        // FL = vx - vy - omega * (trackWidth + wheelBase)/2
        // FR = vx + vy + omega * (trackWidth + wheelBase)/2
        // RL = vx + vy - omega * (trackWidth + wheelBase)/2
        // RR = vx - vy + omega * (trackWidth + wheelBase)/2
        
        val flV = actualVx - actualVy - actualOmega * 0.45
        val frV = actualVx + actualVy + actualOmega * 0.45
        val rlV = actualVx + actualVy - actualOmega * 0.45
        val rrV = actualVx - actualVy + actualOmega * 0.45

        fl.velocity = flV * encoderTicksPerMeter
        fr.velocity = frV * encoderTicksPerMeter
        rl.velocity = rlV * encoderTicksPerMeter
        rr.velocity = rrV * encoderTicksPerMeter

        fl.currentPosition += (fl.velocity * dt).toInt()
        fr.currentPosition += (fr.velocity * dt).toInt()
        rl.currentPosition += (rl.velocity * dt).toInt()
        rr.currentPosition += (rr.velocity * dt).toInt()

        // Feed simulated EKF/Pinpoint sensor coordinates
        val xOff = pinpoint.xOffsetMeters
        val yOff = pinpoint.yOffsetMeters
        val cosH = kotlin.math.cos(trueHeadingRad)
        val sinH = kotlin.math.sin(trueHeadingRad)
        pinpoint.posX = trueX + (xOff * cosH - yOff * sinH)
        pinpoint.posY = trueY + (xOff * sinH + yOff * cosH)
        pinpoint.trueHeading = trueHeadingRad
        pinpoint.heading = if (isPinpointCcwPositive) trueHeadingRad else -trueHeadingRad
        pinpoint.headingVelocity = if (isPinpointCcwPositive) actualOmega else -actualOmega
        pinpoint.velX = actualVx
        pinpoint.velY = actualVy
    }
}
