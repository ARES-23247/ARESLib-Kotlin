package com.areslib.ftc.drivetrain

import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.PIDFCoefficients
import com.areslib.ftc.hardware.CachedDcMotorEx
import com.areslib.hardware.HardwareRegistry
import com.areslib.util.RobotClock

/**
 * Manages the physical 4-motor hardware cluster (FL, FR, RL, RR) for an FTC Mecanum drivetrain.
 * Handles CachedDcMotorEx wrapping, direction mapping, encoder modes, and safety power writes.
 */
class MecanumMotorCluster(
    val hardwareMap: HardwareMap,
    val flName: String = "fl",
    val frName: String = "fr",
    val rlName: String = "rl",
    val rrName: String = "rr",
    val flDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    val frDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE,
    val rlDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    val rrDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE,
    val useClosedLoopVelocity: Boolean = false,
    val motorKp: Double? = null,
    val motorKi: Double? = null,
    val motorKd: Double? = null,
    val motorKf: Double? = null
) : AutoCloseable {

    val frontLeft: DcMotorEx = CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, flName))
    val frontRight: DcMotorEx = CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, frName))
    val rearLeft: DcMotorEx = CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, rlName))
    val rearRight: DcMotorEx = CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, rrName))

    val flIO = EstimateMotorIO(frontLeft)
    val frIO = EstimateMotorIO(frontRight)
    val rlIO = EstimateMotorIO(rearLeft)
    val rrIO = EstimateMotorIO(rearRight)

    private var lastWarningTime = 0L

    init {
        frontLeft.direction = flDirection
        frontRight.direction = frDirection
        rearLeft.direction = rlDirection
        rearRight.direction = rrDirection

        HardwareRegistry.registerMotor(flName, flIO)
        HardwareRegistry.registerMotor(frName, frIO)
        HardwareRegistry.registerMotor(rlName, rlIO)
        HardwareRegistry.registerMotor(rrName, rrIO)

        HardwareRegistry.registerSyncPolledDevice(flIO)
        HardwareRegistry.registerSyncPolledDevice(frIO)
        HardwareRegistry.registerSyncPolledDevice(rlIO)
        HardwareRegistry.registerSyncPolledDevice(rrIO)

        if (useClosedLoopVelocity) {
            listOf(frontLeft, frontRight, rearLeft, rearRight).forEach { motor ->
                motor.mode = DcMotor.RunMode.RUN_USING_ENCODER
            }
            if (motorKp != null || motorKi != null || motorKd != null || motorKf != null) {
                val coefficients = PIDFCoefficients(
                    motorKp ?: 0.0, motorKi ?: 0.0, motorKd ?: 0.0, motorKf ?: 0.0
                )
                listOf(frontLeft, frontRight, rearLeft, rearRight).forEach { motor ->
                    motor.setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER, coefficients)
                }
            }
        } else {
            listOf(frontLeft, frontRight, rearLeft, rearRight).forEach { motor ->
                motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
            }
        }
    }

    fun setMotorPowers(fl: Double, fr: Double, rl: Double, rr: Double) {
        safeSetPower(frontLeft, fl, "frontLeft")
        safeSetPower(frontRight, fr, "frontRight")
        safeSetPower(rearLeft, rl, "rearLeft")
        safeSetPower(rearRight, rr, "rearRight")

        flIO.power = fl
        frIO.power = fr
        rlIO.power = rl
        rrIO.power = rr
    }

    fun applyPowerScale(scale: Double) {
        val s = scale.coerceIn(0.0, 1.0)
        flIO.powerScale = s
        frIO.powerScale = s
        rlIO.powerScale = s
        rrIO.powerScale = s
    }

    fun updateInputs() {
        flIO.updateInputs()
        frIO.updateInputs()
        rlIO.updateInputs()
        rrIO.updateInputs()
    }

    fun safe() {
        safeSetPower(frontLeft, 0.0, "frontLeft")
        safeSetPower(frontRight, 0.0, "frontRight")
        safeSetPower(rearLeft, 0.0, "rearLeft")
        safeSetPower(rearRight, 0.0, "rearRight")
        flIO.power = 0.0
        frIO.power = 0.0
        rlIO.power = 0.0
        rrIO.power = 0.0
    }

    private fun safeSetPower(motor: DcMotorEx, power: Double, name: String) {
        try {
            motor.power = power
        } catch (e: Exception) {
            val now = RobotClock.currentTimeMillis()
            if (now - lastWarningTime > 2000L) {
                System.err.println("MecanumMotorCluster: Failed to set $name power. Error: ${e.message}")
                lastWarningTime = now
            }
        }
    }

    override fun close() {
        flIO.close()
        frIO.close()
        rlIO.close()
        rrIO.close()
    }
}
