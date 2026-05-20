package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.hardware.MotorIO
import com.areslib.math.SlewRateLimiter

class MecanumHardwareIO @kotlin.jvm.JvmOverloads constructor(
    hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br",
    private val maxWheelSpeedMetersPerSecond: Double = 3.5
) {
    private val frontLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, flName)
    private val frontRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, frName)
    private val backLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, blName)
    private val backRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, brName)

    /** Lightweight MotorIO wrappers for software estimation (CurrentBudgetManager) */
    val flIO = EstimateMotorIO(frontLeft)
    val frIO = EstimateMotorIO(frontRight)
    val blIO = EstimateMotorIO(backLeft)
    val brIO = EstimateMotorIO(backRight)

    private var flLimiter: SlewRateLimiter? = null
    private var frLimiter: SlewRateLimiter? = null
    private var blLimiter: SlewRateLimiter? = null
    private var brLimiter: SlewRateLimiter? = null

    /**
     * The active slew rate limit in power units per second (e.g. 5.0 means full acceleration takes 0.2s).
     * Set to null to disable rate limiting (default).
     */
    var slewRateLimit: Double? = null
        set(value) {
            field = value
            if (value != null) {
                flLimiter = SlewRateLimiter(value)
                frLimiter = SlewRateLimiter(value)
                blLimiter = SlewRateLimiter(value)
                brLimiter = SlewRateLimiter(value)
            } else {
                flLimiter = null
                frLimiter = null
                blLimiter = null
                brLimiter = null
            }
        }

    /**
     * Set to true to dynamically scale the positive slew rate limit (acceleration)
     * downwards during battery voltage sag to prevent brownout.
     */
    var enableVoltageCompensatedSlew: Boolean = false

    init {
        // Typical mecanum configuration requires right side to be reversed
        frontLeft.direction = DcMotorSimple.Direction.FORWARD
        backLeft.direction = DcMotorSimple.Direction.FORWARD
        frontRight.direction = DcMotorSimple.Direction.REVERSE
        backRight.direction = DcMotorSimple.Direction.REVERSE
    }

    /**
     * Applies the calculated wheel speeds to the physical motors using voltage compensation.
     * @param speeds The wheel speeds (in meters per second).
     * @param batteryVolts The current battery voltage to compensate for sag.
     * @param dtSeconds The time step elapsed since the last update in seconds.
     */
    @kotlin.jvm.JvmOverloads
    fun apply(speeds: MecanumWheelSpeeds, batteryVolts: Double = 12.0, dtSeconds: Double = 0.02) {
        val maxVolts = 12.0
        val actualVolts = if (batteryVolts > 0.1) batteryVolts else 12.0
        val voltageCompensationFactor = maxVolts / actualVolts
        
        var flPower = ((speeds.frontLeftMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)
        var frPower = ((speeds.frontRightMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)
        var blPower = ((speeds.backLeftMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)
        var brPower = ((speeds.backRightMetersPerSecond / maxWheelSpeedMetersPerSecond) * voltageCompensationFactor).coerceIn(-1.0, 1.0)

        // Adjust positive slew rate limits based on battery voltage if enabled
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
            blLimiter?.setRateLimits(posLimit, -baseLimit)
            brLimiter?.setRateLimits(posLimit, -baseLimit)
        }

        // Apply slew rate limiters if configured
        flLimiter?.let { flPower = it.calculate(flPower, dtSeconds) }
        frLimiter?.let { frPower = it.calculate(frPower, dtSeconds) }
        blLimiter?.let { blPower = it.calculate(blPower, dtSeconds) }
        brLimiter?.let { brPower = it.calculate(brPower, dtSeconds) }

        // Normalize meters per second speed into [-1.0, 1.0] range and apply compensation
        frontLeft.power = flPower
        frontRight.power = frPower
        backLeft.power = blPower
        backRight.power = brPower

        // Update tracking values for current estimation (no I2C overhead)
        flIO.power = flPower
        frIO.power = frPower
        blIO.power = blPower
        brIO.power = brPower
    }

    /**
     * Applies a global power scale factor to all 4 drive motors.
     * Used by the BrownoutGuard to reduce output during voltage sag.
     * @param scale Power multiplier (0.0 = disabled, 1.0 = full power)
     */
    fun applyPowerScale(scale: Double) {
        val s = scale.coerceIn(0.0, 1.0)
        frontLeft.power = frontLeft.power * s
        frontRight.power = frontRight.power * s
        backLeft.power = backLeft.power * s
        backRight.power = backRight.power * s

        flIO.powerScale = s
        frIO.powerScale = s
        blIO.powerScale = s
        brIO.powerScale = s
    }
}

/**
 * A lightweight MotorIO wrapper that records target power changes locally to avoid 
 * making blocking I2C current/power writes or reads when estimating current draw.
 */
class EstimateMotorIO(private val motor: DcMotorEx) : MotorIO {
    override var power: Double = 0.0
    override var powerScale: Double = 1.0

    override val velocity: Double
        get() = try {
            motor.velocity
        } catch (_: Exception) {
            0.0
        }

    override val position: Double
        get() = try {
            motor.currentPosition.toDouble()
        } catch (_: Exception) {
            0.0
        }

    override val currentAmps: Double
        get() = try {
            motor.getCurrent(org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit.AMPS)
        } catch (_: Exception) {
            0.0
        }

    override fun resetEncoder() {
        // No-op to avoid side-effects in estimation wrapper
    }
}
