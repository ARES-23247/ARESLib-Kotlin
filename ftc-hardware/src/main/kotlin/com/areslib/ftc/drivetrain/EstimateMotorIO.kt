package com.areslib.ftc.drivetrain

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.areslib.hardware.actuator.MotorIO
import com.areslib.hardware.SyncPolledDevice
import com.areslib.util.RobotClock
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit

/**
 * Non-blocking motor IO wrapper for REV Expansion Hub `DcMotorEx` actuators.
 *
 * Caches position, calculated velocity, and electrical current draw locally to prevent blocking reads/writes
 * on high-frequency robot control loops.
 *
 * @param motor FTC `DcMotorEx` hardware map instance.
 */
class EstimateMotorIO(private val motor: DcMotorEx) : MotorIO, AutoCloseable, SyncPolledDevice {
    override var power: Double = 0.0
    override var powerScale: Double = 1.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0
    private var cachedAmps = 0.0

    private var lastPosition = 0.0
    private var lastTime = 0L

    /** Synchronously updates electrical current draw reading in Amperes. */
    override fun pollSync() {
        try {
            cachedAmps = motor.getCurrent(CurrentUnit.AMPS)
        } catch (_: Exception) {}
    }

    /** Updates local position and velocity estimates from REV bulk-read data. */
    fun updateInputs() {
        try {
            cachedPosition = motor.currentPosition.toDouble()
            val now = RobotClock.currentTimeMillis()
            if (lastTime != 0L) {
                val dt = (now - lastTime) / 1000.0
                if (dt > 0.0) {
                    cachedVelocity = (cachedPosition - lastPosition) / dt
                }
            }
            lastPosition = cachedPosition
            lastTime = now
        } catch (_: Exception) {}
    }

    /** Measured motor velocity in encoder ticks per second. */
    override val velocity: Double
        get() = cachedVelocity

    /** Measured motor position in total cumulative encoder ticks. */
    override val position: Double
        get() = cachedPosition

    /** Measured electrical current draw in Amperes ($A$). */
    override val currentAmps: Double
        get() = cachedAmps

    /** Resets the local motor encoder zero reference. */
    override fun resetEncoder() {
        // No-op to avoid side-effects in estimation wrapper
    }

    /** Releases hardware resources upon OpMode termination. */
    override fun close() {
    }
}
