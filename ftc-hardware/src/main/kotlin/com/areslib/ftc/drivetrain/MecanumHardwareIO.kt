package com.areslib.ftc.drivetrain

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.hardware.actuator.MotorIO
import com.areslib.math.filter.SlewRateLimiter
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MecanumHardwareIO @kotlin.jvm.JvmOverloads constructor(
    val hardwareMap: HardwareMap,
    val flName: String = "fl",
    val frName: String = "fr",
    val rlName: String = "rl",
    val rrName: String = "rr",
    var maxWheelSpeedMetersPerSecond: Double = 3.5,
    val flDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    val frDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE,
    val rlDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    val rrDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE,
    initialKs: Double = 0.0,
    val initialSlewRateLimit: Double? = null,
    val motorKp: Double? = null,
    val motorKi: Double? = null,
    val motorKd: Double? = null,
    val motorKf: Double? = null
) : com.areslib.hardware.SubsystemIO, AutoCloseable {
    val frontLeft: DcMotorEx = com.areslib.ftc.hardware.CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, flName))
    val frontRight: DcMotorEx = com.areslib.ftc.hardware.CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, frName))
    val rearLeft: DcMotorEx = com.areslib.ftc.hardware.CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, rlName))
    val rearRight: DcMotorEx = com.areslib.ftc.hardware.CachedDcMotorEx(hardwareMap.get(DcMotorEx::class.java, rrName))
    
    /** Static friction feedforward coefficient (power needed to overcome static drivetrain friction) */
    var kS: Double = initialKs

    /** Lightweight MotorIO wrappers for software estimation (CurrentBudgetManager) */
    val flIO = EstimateMotorIO(frontLeft)
    val frIO = EstimateMotorIO(frontRight)
    val rlIO = EstimateMotorIO(rearLeft)
    val rrIO = EstimateMotorIO(rearRight)
    private val speedBuffer = DoubleArray(4)

    private var flLimiter: SlewRateLimiter? = null
    private var frLimiter: SlewRateLimiter? = null
    private var rlLimiter: SlewRateLimiter? = null
    private var rrLimiter: SlewRateLimiter? = null

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
                rlLimiter = SlewRateLimiter(value)
                rrLimiter = SlewRateLimiter(value)
            } else {
                flLimiter = null
                frLimiter = null
                rlLimiter = null
                rrLimiter = null
            }
        }

    /**
     * Set to true to dynamically scale the positive slew rate limit (acceleration)
     * downwards during battery voltage sag to prevent brownout.
     */
    var enableVoltageCompensatedSlew: Boolean = false

    init {
        frontLeft.direction = flDirection
        frontRight.direction = frDirection
        rearLeft.direction = rlDirection
        rearRight.direction = rrDirection

        // Register drive motors automatically for automatic diagnostics and current budgeting
        com.areslib.hardware.HardwareRegistry.registerMotor(flName, flIO)
        com.areslib.hardware.HardwareRegistry.registerMotor(frName, frIO)
        com.areslib.hardware.HardwareRegistry.registerMotor(rlName, rlIO)
        com.areslib.hardware.HardwareRegistry.registerMotor(rrName, rrIO)
        com.areslib.hardware.HardwareRegistry.registerDevice("Drivetrain/Mecanum", this)
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)

        if (motorKp != null || motorKi != null || motorKd != null || motorKf != null) {
            val coefficients = com.qualcomm.robotcore.hardware.PIDFCoefficients(
                motorKp ?: 0.0, motorKi ?: 0.0, motorKd ?: 0.0, motorKf ?: 0.0
            )
            listOf(frontLeft, frontRight, rearLeft, rearRight).forEach { motor ->
                motor.setPIDFCoefficients(com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_USING_ENCODER, coefficients)
            }
        }

        if (initialSlewRateLimit != null) {
            slewRateLimit = initialSlewRateLimit
        }
    }

    override fun close() {
        flIO.close()
        frIO.close()
        rlIO.close()
        rrIO.close()
    }

    override fun refresh() {
        updateInputs()
    }

    override fun safe() {
        safeSetPower(frontLeft, 0.0, "frontLeft")
        safeSetPower(frontRight, 0.0, "frontRight")
        safeSetPower(rearLeft, 0.0, "rearLeft")
        safeSetPower(rearRight, 0.0, "rearRight")
        flIO.power = 0.0
        frIO.power = 0.0
        rlIO.power = 0.0
        rrIO.power = 0.0
    }

    /**
     * Coordinate-centric Mecanum drive calculation wrapper.
     */
    fun drive(
        driveState: com.areslib.state.DriveState,
        kinematics: com.areslib.kinematics.MecanumKinematics,
        batteryVolts: Double,
        dtSeconds: Double
    ) {
        val maxSpeed = maxWheelSpeedMetersPerSecond
        val omega = driveState.angularVelocityRadiansPerSecond * (maxSpeed / kinematics.k)

        val forward = driveState.yVelocityMetersPerSecond * maxSpeed
        val left = -driveState.xVelocityMetersPerSecond * maxSpeed

        kinematics.toWheelSpeeds(forward, left, omega, speedBuffer)
        com.areslib.kinematics.MecanumKinematics.normalize(speedBuffer, maxSpeed)

        apply(
            speeds = speedBuffer,
            batteryVolts = batteryVolts,
            dtSeconds = dtSeconds,
            powerScale = flIO.powerScale
        )
        
        applyPowerScale(flIO.powerScale)
    }

    /**
     * Applies the calculated wheel speeds to the physical motors using voltage compensation and power scaling.
     * @param speeds The wheel speeds (in meters per second).
     * @param batteryVolts The current battery voltage to compensate for sag.
     * @param dtSeconds The time step elapsed since the last update in seconds.
     * @param powerScale The power multiplier (0.0 to 1.0) for safety/brownout throttling.
     */
    @kotlin.jvm.JvmOverloads
    fun apply(speeds: DoubleArray, batteryVolts: Double = 12.0, dtSeconds: Double = 0.02, powerScale: Double = 1.0) {
        if (speeds.size < 4) return
        val maxVolts = 12.0
        val actualVolts = if (batteryVolts > 0.1) batteryVolts else 12.0
        val voltageCompensationFactor = maxVolts / actualVolts
        
        fun applyFeedforward(speedMetersPerSecond: Double): Double {
            if (kotlin.math.abs(speedMetersPerSecond) < 1e-4) return 0.0
            val sign = kotlin.math.sign(speedMetersPerSecond)
            val velocityFF = speedMetersPerSecond / maxWheelSpeedMetersPerSecond
            val staticFF = sign * kS
            return (velocityFF + staticFF) * voltageCompensationFactor
        }
        
        var flPower = (applyFeedforward(speeds[0]) * powerScale).coerceIn(-1.0, 1.0)
        var frPower = (applyFeedforward(speeds[1]) * powerScale).coerceIn(-1.0, 1.0)
        var rlPower = (applyFeedforward(speeds[2]) * powerScale).coerceIn(-1.0, 1.0)
        var rrPower = (applyFeedforward(speeds[3]) * powerScale).coerceIn(-1.0, 1.0)

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
            rlLimiter?.setRateLimits(posLimit, -baseLimit)
            rrLimiter?.setRateLimits(posLimit, -baseLimit)
        }

        // Apply slew rate limiters if configured
        flLimiter?.let { flPower = it.calculate(flPower, dtSeconds) }
        frLimiter?.let { frPower = it.calculate(frPower, dtSeconds) }
        rlLimiter?.let { rlPower = it.calculate(rlPower, dtSeconds) }
        rrLimiter?.let { rrPower = it.calculate(rrPower, dtSeconds) }

        // Normalize meters per second speed into [-1.0, 1.0] range and apply compensation
        safeSetPower(frontLeft, flPower, "frontLeft")
        safeSetPower(frontRight, frPower, "frontRight")
        safeSetPower(rearLeft, rlPower, "rearLeft")
        safeSetPower(rearRight, rrPower, "rearRight")

        // Update tracking values for current estimation (no I2C overhead)
        flIO.power = flPower
        frIO.power = frPower
        rlIO.power = rlPower
        rrIO.power = rrPower
    }

    @kotlin.jvm.JvmOverloads
    fun apply(speeds: MecanumWheelSpeeds, batteryVolts: Double = 12.0, dtSeconds: Double = 0.02, powerScale: Double = 1.0) {
        speedBuffer[0] = speeds.frontLeftMetersPerSecond
        speedBuffer[1] = speeds.frontRightMetersPerSecond
        speedBuffer[2] = speeds.backLeftMetersPerSecond
        speedBuffer[3] = speeds.backRightMetersPerSecond
        apply(speedBuffer, batteryVolts, dtSeconds, powerScale)
    }

    /**
     * Applies a global power scale factor to all 4 drive motor estimation wrappers.
     * @param scale Power multiplier (0.0 = disabled, 1.0 = full power)
     */
    fun applyPowerScale(scale: Double) {
        val s = scale.coerceIn(0.0, 1.0)
        flIO.powerScale = s
        frIO.powerScale = s
        rlIO.powerScale = s
        rrIO.powerScale = s
    }

    /**
     * Set direct powers to the 4 drive motors bypassing kinematics.
     */
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

    private var currentPollIndex = 0

    /**
     * Updates cached motor sensor readings from the hardware bulk read caches.
     * Call this at the start of every loop iteration to ensure fresh, zero-overhead sensor access.
     */
    fun updateInputs() {
        flIO.updateInputs()
        frIO.updateInputs()
        rlIO.updateInputs()
        rrIO.updateInputs()
        
        when (currentPollIndex) {
            0 -> flIO.pollCurrentSync()
            1 -> frIO.pollCurrentSync()
            2 -> rlIO.pollCurrentSync()
            3 -> rrIO.pollCurrentSync()
        }
        currentPollIndex = (currentPollIndex + 1) % 4
    }

    private var lastWarningTime = 0L

    private fun safeSetPower(motor: DcMotorEx, power: Double, name: String) {
        try {
            motor.power = power
        } catch (e: Exception) {
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            if (now - lastWarningTime > 2000L) {
                System.err.println("MecanumHardwareIO: Failed to set $name power. Error: ${e.message}")
                lastWarningTime = now
            }
        }
    }

    private fun safeGetPower(motor: DcMotorEx): Double {
        return try {
            motor.power
        } catch (_: Exception) {
            0.0
        }
    }
}

/**
 * A lightweight MotorIO wrapper that records target power changes locally to avoid 
 * making blocking I2C current/power writes or reads when estimating current draw.
 */
class EstimateMotorIO(private val motor: DcMotorEx) : MotorIO, AutoCloseable {
    override var power: Double = 0.0
    override var powerScale: Double = 1.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0
    private var cachedAmps = 0.0

    fun pollCurrentSync() {
        try {
            cachedAmps = motor.getCurrent(org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit.AMPS)
        } catch (_: Exception) {}
    }

    /** Updates local caches from the bulk-cached register maps */
    fun updateInputs() {
        try {
            cachedPosition = motor.currentPosition.toDouble()
        } catch (_: Exception) {}
        try {
            cachedVelocity = motor.velocity
        } catch (_: Exception) {}
    }

    override val velocity: Double
        get() = cachedVelocity

    override val position: Double
        get() = cachedPosition

    override val currentAmps: Double
        get() = cachedAmps

    override fun resetEncoder() {
        // No-op to avoid side-effects in estimation wrapper
    }

    override fun close() {
    }
}

