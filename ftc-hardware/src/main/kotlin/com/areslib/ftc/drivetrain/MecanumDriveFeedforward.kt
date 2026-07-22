package com.areslib.ftc.drivetrain

import com.areslib.control.feedback.PIDController
import com.areslib.math.filter.SlewRateLimiter
import kotlin.math.abs
import kotlin.math.sign

/**
 * Manages static friction feedforward ($k_S$), closed-loop wheel velocity PID feedback controllers,
 * slew rate limiters, and voltage-compensated acceleration scaling for mecanum drivetrains.
 */
class MecanumDriveFeedforward(
    var initialKs: Double = 0.0,
    var motorKp: Double? = null,
    var motorKi: Double? = null,
    var motorKd: Double? = null,
    var initialSlewRateLimit: Double? = null
) {
    var kS: Double = initialKs

    private var flController = if (motorKp != null) PIDController(motorKp!!, motorKi ?: 0.0, motorKd ?: 0.0) else null
    private var frController = if (motorKp != null) PIDController(motorKp!!, motorKi ?: 0.0, motorKd ?: 0.0) else null
    private var rlController = if (motorKp != null) PIDController(motorKp!!, motorKi ?: 0.0, motorKd ?: 0.0) else null
    private var rrController = if (motorKp != null) PIDController(motorKp!!, motorKi ?: 0.0, motorKd ?: 0.0) else null

    private var flLimiter: SlewRateLimiter? = null
    private var frLimiter: SlewRateLimiter? = null
    private var rlLimiter: SlewRateLimiter? = null
    private var rrLimiter: SlewRateLimiter? = null

    var slewRateLimit: Double? = initialSlewRateLimit
        set(value) {
            field = value
            if (value != null) {
                flLimiter = SlewRateLimiter(value)
                frLimiter = SlewRateLimiter(value)
                rlLimiter = SlewRateLimiter(value)
                rrLimiter = SlewRateLimiter(value)
            } else {
                flLimiter = null
                frLimiter = null
                rlLimiter = null
                rrLimiter = null
            }
        }

    var enableVoltageCompensatedSlew: Boolean = false

    init {
        flController?.setOutputLimits(-1.0, 1.0)
        frController?.setOutputLimits(-1.0, 1.0)
        rlController?.setOutputLimits(-1.0, 1.0)
        rrController?.setOutputLimits(-1.0, 1.0)

        if (initialSlewRateLimit != null) {
            slewRateLimit = initialSlewRateLimit
        }
    }

    fun updateMotorGains(kp: Double, ki: Double, kd: Double) {
        val fl = flController
        if (fl == null) {
            flController = PIDController(kp, ki, kd).apply { setOutputLimits(-1.0, 1.0) }
            frController = PIDController(kp, ki, kd).apply { setOutputLimits(-1.0, 1.0) }
            rlController = PIDController(kp, ki, kd).apply { setOutputLimits(-1.0, 1.0) }
            rrController = PIDController(kp, ki, kd).apply { setOutputLimits(-1.0, 1.0) }
        } else {
            fl.p = kp; fl.i = ki; fl.d = kd
            frController?.let { it.p = kp; it.i = ki; it.d = kd }
            rlController?.let { it.p = kp; it.i = ki; it.d = kd }
            rrController?.let { it.p = kp; it.i = ki; it.d = kd }
        }
    }

    fun calculateMotorPowers(
        speeds: DoubleArray,
        maxWheelSpeedMps: Double,
        batteryVolts: Double,
        dtSeconds: Double,
        powerScale: Double,
        useClosedLoopVelocity: Boolean,
        ticksPerMeter: Double,
        flVel: Double,
        frVel: Double,
        rlVel: Double,
        rrVel: Double,
        outputPowers: DoubleArray
    ) {
        if (speeds.size < 4 || outputPowers.size < 4) return
        val maxVolts = 12.0
        val actualVolts = if (batteryVolts > 0.1) batteryVolts else 12.0
        val voltageCompensationFactor = maxVolts / actualVolts

        fun applyFeedforward(speedMetersPerSecond: Double): Double {
            if (abs(speedMetersPerSecond) < 1e-4) return 0.0
            val sign = sign(speedMetersPerSecond)
            val velocityFF = speedMetersPerSecond / maxWheelSpeedMps
            val staticFF = sign * kS
            return (velocityFF + staticFF) * voltageCompensationFactor
        }

        if (abs(speeds[0]) < 1e-4) flController?.reset()
        if (abs(speeds[1]) < 1e-4) frController?.reset()
        if (abs(speeds[2]) < 1e-4) rlController?.reset()
        if (abs(speeds[3]) < 1e-4) rrController?.reset()

        val fl = flController
        val fr = frController
        val rl = rlController
        val rr = rrController

        val flFeedback = if (!useClosedLoopVelocity && fl != null) fl.calculate(flVel / ticksPerMeter, speeds[0], dtSeconds) else 0.0
        val frFeedback = if (!useClosedLoopVelocity && fr != null) fr.calculate(frVel / ticksPerMeter, speeds[1], dtSeconds) else 0.0
        val rlFeedback = if (!useClosedLoopVelocity && rl != null) rl.calculate(rlVel / ticksPerMeter, speeds[2], dtSeconds) else 0.0
        val rrFeedback = if (!useClosedLoopVelocity && rr != null) rr.calculate(rrVel / ticksPerMeter, speeds[3], dtSeconds) else 0.0

        var flPower = ((applyFeedforward(speeds[0]) + flFeedback) * powerScale).coerceIn(-1.0, 1.0)
        var frPower = ((applyFeedforward(speeds[1]) + frFeedback) * powerScale).coerceIn(-1.0, 1.0)
        var rlPower = ((applyFeedforward(speeds[2]) + rlFeedback) * powerScale).coerceIn(-1.0, 1.0)
        var rrPower = ((applyFeedforward(speeds[3]) + rrFeedback) * powerScale).coerceIn(-1.0, 1.0)

        val baseLimit = slewRateLimit
        if (baseLimit != null) {
            val posLimit = if (enableVoltageCompensatedSlew) {
                val scale = ((actualVolts - 7.5) / (12.0 - 7.5)).coerceIn(0.2, 1.0)
                baseLimit * scale
            } else {
                baseLimit
            }
            flLimiter?.setRateLimits(posLimit, -baseLimit)
            frLimiter?.setRateLimits(posLimit, -baseLimit)
            rlLimiter?.setRateLimits(posLimit, -baseLimit)
            rrLimiter?.setRateLimits(posLimit, -baseLimit)
        }

        flLimiter?.let { flPower = it.calculate(flPower, dtSeconds) }
        frLimiter?.let { frPower = it.calculate(frPower, dtSeconds) }
        rlLimiter?.let { rlPower = it.calculate(rlPower, dtSeconds) }
        rrLimiter?.let { rrPower = it.calculate(rrPower, dtSeconds) }

        outputPowers[0] = flPower
        outputPowers[1] = frPower
        outputPowers[2] = rlPower
        outputPowers[3] = rrPower
    }
}
