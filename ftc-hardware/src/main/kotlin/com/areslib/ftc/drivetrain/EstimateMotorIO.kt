package com.areslib.ftc.drivetrain

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.areslib.hardware.actuator.MotorIO
import com.areslib.hardware.SyncPolledDevice
import com.areslib.util.RobotClock
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit

/**
 * A lightweight MotorIO wrapper that records target power changes locally to avoid 
 * making blocking I2C current/power writes or reads when estimating current draw.
 */
class EstimateMotorIO(private val motor: DcMotorEx) : MotorIO, AutoCloseable, SyncPolledDevice {
    override var power: Double = 0.0
    override var powerScale: Double = 1.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0
    private var cachedAmps = 0.0

    private var lastPosition = 0.0
    private var lastTime = 0L

    override fun pollSync() {
        try {
            cachedAmps = motor.getCurrent(CurrentUnit.AMPS)
        } catch (_: Exception) {}
    }

    /** Updates local caches from the bulk-cached register maps */
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
