package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.hardware.MotorIO
import com.areslib.math.SlewRateLimiter
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
    val blName: String = "bl",
    val brName: String = "br",
    val maxWheelSpeedMetersPerSecond: Double = 3.5,
    val flDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    val frDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE,
    val blDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    val brDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE
) : com.areslib.hardware.SubsystemIO, AutoCloseable {
    val frontLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, flName)
    val frontRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, frName)
    val backLeft: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, blName)
    val backRight: DcMotorEx = hardwareMap.get(DcMotorEx::class.java, brName)
    
    /** Static friction feedforward coefficient (power needed to overcome static drivetrain friction) */
    var kS: Double = 0.0

    /** Lightweight MotorIO wrappers for software estimation (CurrentBudgetManager) */
    val flIO = EstimateMotorIO(frontLeft)
    val frIO = EstimateMotorIO(frontRight)
    val blIO = EstimateMotorIO(backLeft)
    val brIO = EstimateMotorIO(backRight)
    private val speedBuffer = DoubleArray(4)

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
        frontLeft.direction = flDirection
        frontRight.direction = frDirection
        backLeft.direction = blDirection
        backRight.direction = brDirection

        // Register drive motors automatically for automatic diagnostics and current budgeting
        com.areslib.hardware.HardwareRegistry.registerMotor(flName, flIO)
        com.areslib.hardware.HardwareRegistry.registerMotor(frName, frIO)
        com.areslib.hardware.HardwareRegistry.registerMotor(blName, blIO)
        com.areslib.hardware.HardwareRegistry.registerMotor(brName, brIO)
        com.areslib.hardware.HardwareRegistry.registerDevice("Drivetrain/Mecanum", this)
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)
    }

    override fun close() {
        flIO.close()
        frIO.close()
        blIO.close()
        brIO.close()
    }

    override fun refresh() {
        updateInputs()
    }

    override fun safe() {
        safeSetPower(frontLeft, 0.0, "frontLeft")
        safeSetPower(frontRight, 0.0, "frontRight")
        safeSetPower(backLeft, 0.0, "backLeft")
        safeSetPower(backRight, 0.0, "backRight")
        flIO.power = 0.0
        frIO.power = 0.0
        blIO.power = 0.0
        brIO.power = 0.0
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
        val vx = driveState.xVelocityMetersPerSecond * maxSpeed
        val vy = driveState.yVelocityMetersPerSecond * maxSpeed
        val omega = driveState.angularVelocityRadiansPerSecond * maxSpeed

        var robotVx = vx
        var robotVy = vy
        if (driveState.isFieldCentric) {
            val heading = driveState.poseEstimator.estimatedPose.heading.radians
            val cos = kotlin.math.cos(heading)
            val sin = kotlin.math.sin(heading)
            robotVx = vx * cos + vy * sin
            robotVy = -vx * sin + vy * cos
        }

        kinematics.toWheelSpeeds(robotVx, robotVy, omega, speedBuffer)
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
        var blPower = (applyFeedforward(speeds[2]) * powerScale).coerceIn(-1.0, 1.0)
        var brPower = (applyFeedforward(speeds[3]) * powerScale).coerceIn(-1.0, 1.0)

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
        safeSetPower(frontLeft, flPower, "frontLeft")
        safeSetPower(frontRight, frPower, "frontRight")
        safeSetPower(backLeft, blPower, "backLeft")
        safeSetPower(backRight, brPower, "backRight")

        // Update tracking values for current estimation (no I2C overhead)
        flIO.power = flPower
        frIO.power = frPower
        blIO.power = blPower
        brIO.power = brPower
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
        blIO.powerScale = s
        brIO.powerScale = s
    }

    /**
     * Set direct powers to the 4 drive motors bypassing kinematics.
     */
    fun setMotorPowers(fl: Double, fr: Double, bl: Double, br: Double) {
        safeSetPower(frontLeft, fl, "frontLeft")
        safeSetPower(frontRight, fr, "frontRight")
        safeSetPower(backLeft, bl, "backLeft")
        safeSetPower(backRight, br, "backRight")
        
        flIO.power = fl
        frIO.power = fr
        blIO.power = bl
        brIO.power = br
    }

    /**
     * Updates cached motor sensor readings from the hardware bulk read caches.
     * Call this at the start of every loop iteration to ensure fresh, zero-overhead sensor access.
     */
    fun updateInputs() {
        flIO.updateInputs()
        frIO.updateInputs()
        blIO.updateInputs()
        brIO.updateInputs()
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            while (isActive) {
                try {
                    cachedAmps = motor.getCurrent(org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit.AMPS)
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(100)
            }
        }
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
        scope.cancel()
    }
}
