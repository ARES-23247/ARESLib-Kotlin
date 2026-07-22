package com.areslib.ftc.drivetrain

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.HardwareMap
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.hardware.SubsystemIO
import com.areslib.hardware.HardwareRegistry

/**
 * Facade class combining physical motor hardware cluster management with drive motion feedforwards.
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

    fun updateMotorGains(kp: Double, ki: Double, kd: Double) {
        feedforward.updateMotorGains(kp, ki, kd)
    }

    override fun close() {
        motorCluster.close()
    }

    override fun refresh() {
        updateInputs()
    }

    override fun safe() {
        motorCluster.safe()
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
        val forward = driveState.xVelocityMetersPerSecond * maxSpeed
        val left = driveState.yVelocityMetersPerSecond * maxSpeed

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

    @kotlin.jvm.JvmOverloads
    fun apply(speeds: MecanumWheelSpeeds, batteryVolts: Double = 12.0, dtSeconds: Double = 0.02, powerScale: Double = 1.0) {
        speedBuffer[0] = speeds.frontLeftMetersPerSecond
        speedBuffer[1] = speeds.frontRightMetersPerSecond
        speedBuffer[2] = speeds.backLeftMetersPerSecond
        speedBuffer[3] = speeds.backRightMetersPerSecond
        apply(speedBuffer, batteryVolts, dtSeconds, powerScale)
    }

    fun applyPowerScale(scale: Double) {
        motorCluster.applyPowerScale(scale)
    }

    fun setMotorPowers(fl: Double, fr: Double, rl: Double, rr: Double) {
        motorCluster.setMotorPowers(fl, fr, rl, rr)
    }

    fun updateInputs() {
        motorCluster.updateInputs()
    }
}
