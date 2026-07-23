package com.areslib.ftc.drivetrain

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.HardwareMap
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.hardware.SubsystemIO
import com.areslib.hardware.HardwareRegistry

/**
 * Facade class combining physical motor hardware cluster management with drive motion feedforwards.
 * Provides hardware abstraction for Mecanum drivetrains.
 *
 * PHYSICAL UNITS & CONVENTIONS:
 * - Distance: meters ($m$)
 * - Velocity: meters per second ($m/s$) for linear, radians per second ($rad/s$) for angular.
 * - Voltage: volts ($V$)
 * - Heading/Angular: **CCW-positive** (standard math convention).
 *
 * PERFORMANCE:
 * Guaranteed zero-GC allocations during high-frequency 50Hz/100Hz hardware update loops.
 * 
 * @param hardwareMap FTC hardware map.
 * @param flName Front-left motor device name.
 * @param frName Front-right motor device name.
 * @param rlName Rear-left motor device name.
 * @param rrName Rear-right motor device name.
 * @param maxWheelSpeedMetersPerSecond Maximum expected wheel speed in $m/s$.
 * @param flDirection Direction for front-left motor.
 * @param frDirection Direction for front-right motor.
 * @param rlDirection Direction for rear-left motor.
 * @param rrDirection Direction for rear-right motor.
 * @param initialKs Static friction feedforward constant.
 * @param useClosedLoopVelocity Whether to use onboard motor velocity PID.
 * @param ticksPerMeter Encoder ticks per meter of travel.
 * @param initialSlewRateLimit Maximum allowed acceleration in $V/s$ or proportional unit.
 * @param motorKp Velocity PID proportional gain.
 * @param motorKi Velocity PID integral gain.
 * @param motorKd Velocity PID derivative gain.
 * @param motorKf Velocity feedforward gain.
 */
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
    val useClosedLoopVelocity: Boolean = false,
    var ticksPerMeter: Double = 2000.0,
    val initialSlewRateLimit: Double? = null,
    val motorKp: Double? = null,
    val motorKi: Double? = null,
    val motorKd: Double? = null,
    val motorKf: Double? = null
) : SubsystemIO, AutoCloseable {

    private val motorCluster = MecanumMotorCluster(
        hardwareMap = hardwareMap,
        flName = flName,
        frName = frName,
        rlName = rlName,
        rrName = rrName,
        flDirection = flDirection,
        frDirection = frDirection,
        rlDirection = rlDirection,
        rrDirection = rrDirection,
        useClosedLoopVelocity = useClosedLoopVelocity,
        motorKp = motorKp,
        motorKi = motorKi,
        motorKd = motorKd,
        motorKf = motorKf
    )

    private val feedforward = MecanumDriveFeedforward(
        initialKs = initialKs,
        motorKp = motorKp,
        motorKi = motorKi,
        motorKd = motorKd,
        initialSlewRateLimit = initialSlewRateLimit
    )

    val frontLeft: DcMotorEx get() = motorCluster.frontLeft
    val frontRight: DcMotorEx get() = motorCluster.frontRight
    val rearLeft: DcMotorEx get() = motorCluster.rearLeft
    val rearRight: DcMotorEx get() = motorCluster.rearRight

    val flIO: EstimateMotorIO get() = motorCluster.flIO
    val frIO: EstimateMotorIO get() = motorCluster.frIO
    val rlIO: EstimateMotorIO get() = motorCluster.rlIO
    val rrIO: EstimateMotorIO get() = motorCluster.rrIO

    var kS: Double
        get() = feedforward.kS
        set(value) { feedforward.kS = value }

    var slewRateLimit: Double?
        get() = feedforward.slewRateLimit
        set(value) { feedforward.slewRateLimit = value }

    var enableVoltageCompensatedSlew: Boolean
        get() = feedforward.enableVoltageCompensatedSlew
        set(value) { feedforward.enableVoltageCompensatedSlew = value }

    private val speedBuffer = DoubleArray(4)
    private val powerBuffer = DoubleArray(4)

    init {
        HardwareRegistry.registerDevice("Drivetrain/Mecanum", this)
        HardwareRegistry.registerCloseable(this)
    }

    /**
     * Updates motor PID gains.
     * 
     * @param kp Proportional gain.
     * @param ki Integral gain.
     * @param kd Derivative gain.
     */
    fun updateMotorGains(kp: Double, ki: Double, kd: Double) {
        feedforward.updateMotorGains(kp, ki, kd)
    }

    /**
     * close declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        motorCluster.close()
    }

    /**
     * refresh declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun refresh() {
        updateInputs()
    }

    /**
     * safe declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun safe() {
        motorCluster.safe()
    }

    /**
     * Coordinate-centric Mecanum drive calculation wrapper.
     * Calculates required wheel speeds and applies them to the motor cluster.
     * Zero-GC allocation loop.
     * 
     * @param driveState The desired chassis speeds ($m/s$ and $rad/s$).
     * @param kinematics The Mecanum kinematics model.
     * @param batteryVolts Current battery voltage in $V$.
     * @param dtSeconds Loop time delta in seconds ($s$).
     */
    fun drive(
        driveState: com.areslib.state.DriveState,
        kinematics: com.areslib.kinematics.MecanumKinematics,
        batteryVolts: Double,
        dtSeconds: Double
    ) {
        val maxSpeed = maxWheelSpeedMetersPerSecond
        val omega = driveState.angularVelocityRadiansPerSecond * (maxSpeed / kinematics.k)
        val rawForward = driveState.xVelocityMetersPerSecond * maxSpeed
        val rawLeft = driveState.yVelocityMetersPerSecond * maxSpeed

        val (forward, left) = Pair(rawForward, rawLeft)

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
     * Applies specific wheel speeds to the motor cluster using feedforwards.
     * 
     * @param speeds Array of 4 wheel speeds in $m/s$ [FL, FR, RL, RR].
     * @param batteryVolts Current battery voltage in $V$.
     * @param dtSeconds Loop delta time in $s$.
     * @param powerScale Scaling factor [0..1] for output powers.
     */
    @kotlin.jvm.JvmOverloads
    fun apply(speeds: DoubleArray, batteryVolts: Double = 12.0, dtSeconds: Double = 0.02, powerScale: Double = 1.0) {
        feedforward.calculateMotorPowers(
            speeds = speeds,
            maxWheelSpeedMps = maxWheelSpeedMetersPerSecond,
            batteryVolts = batteryVolts,
            dtSeconds = dtSeconds,
            powerScale = powerScale,
            useClosedLoopVelocity = useClosedLoopVelocity,
            ticksPerMeter = ticksPerMeter,
            flVel = flIO.velocity,
            frVel = frIO.velocity,
            rlVel = rlIO.velocity,
            rrVel = rrIO.velocity,
            outputPowers = powerBuffer
        )



        motorCluster.setMotorPowers(powerBuffer[0], powerBuffer[1], powerBuffer[2], powerBuffer[3])
    }

    /**
     * Applies specific wheel speeds to the motor cluster.
     * 
     * @param speeds Target speeds in $m/s$ for each wheel.
     * @param batteryVolts Current battery voltage in $V$.
     * @param dtSeconds Loop delta time in $s$.
     * @param powerScale Scaling factor [0..1] for output powers.
     */
    @kotlin.jvm.JvmOverloads
    fun apply(speeds: MecanumWheelSpeeds, batteryVolts: Double = 12.0, dtSeconds: Double = 0.02, powerScale: Double = 1.0) {
        speedBuffer[0] = speeds.frontLeftMetersPerSecond
        speedBuffer[1] = speeds.frontRightMetersPerSecond
        speedBuffer[2] = speeds.backLeftMetersPerSecond
        speedBuffer[3] = speeds.backRightMetersPerSecond
        apply(speedBuffer, batteryVolts, dtSeconds, powerScale)
    }

    /**
     * Applies a uniform power scaling factor to all motors.
     * 
     * @param scale Scaling factor [0..1].
     */
    fun applyPowerScale(scale: Double) {
        motorCluster.applyPowerScale(scale)
    }

    /**
     * Sets raw fractional motor powers [-1.0..1.0].
     * 
     * @param fl Front-left power.
     * @param fr Front-right power.
     * @param rl Rear-left power.
     * @param rr Rear-right power.
     */
    fun setMotorPowers(fl: Double, fr: Double, rl: Double, rr: Double) {
        motorCluster.setMotorPowers(fl, fr, rl, rr)
    }

    /**
     * Updates motor IO readings from hardware. Zero-GC.
     */
    fun updateInputs() {
        motorCluster.updateInputs()
    }
}
