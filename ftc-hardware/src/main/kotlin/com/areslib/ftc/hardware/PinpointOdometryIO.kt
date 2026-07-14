package com.areslib.ftc.hardware

import com.areslib.hardware.drive.OdometryIO
import com.areslib.hardware.drive.OdometryInputs
import com.areslib.math.geometry.Pose2d

/**
 * Interface that mirrors the GoBildaPinpointDriver API so we can mock or wrap it.
 * This ensures the ftc-hardware module compiles even if the driver is in TeamCode.
 */
interface PinpointDriverProxy {
    fun update()
    val posX: Double
    val posY: Double
    val heading: Double
    val velX: Double
    val velY: Double
    val headingVelocity: Double
    fun resetPosAndIMU()
}

class PinpointOdometryIO(private val driver: PinpointDriverProxy) : OdometryIO, AutoCloseable {
    private val lock = Any()
    private var running = true

    private var latestPosX = 0.0
    private var latestPosY = 0.0
    private var latestHeading = 0.0
    private var latestVelX = 0.0
    private var latestVelY = 0.0
    private var latestHeadingVelocity = 0.0
    private var latestTimestamp = 0L

    private val thread = Thread {
        while (running) {
            try {
                driver.update()
                val px = driver.posX
                val py = driver.posY
                val h = driver.heading
                val vx = driver.velX
                val vy = driver.velY
                val hv = driver.headingVelocity
                synchronized(lock) {
                    latestPosX = px
                    latestPosY = py
                    latestHeading = -h
                    latestVelX = vx
                    latestVelY = vy
                    latestHeadingVelocity = -hv
                    latestTimestamp = com.areslib.util.RobotClock.currentTimeMillis()
                }
            } catch (_: Exception) {}
            try { Thread.sleep(5) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
        }
    }.apply {
        isDaemon = true
        name = "ARES-PinpointOdometry-Thread"
    }

    init {
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)
        thread.start()
    }

    override fun initialize(startPose: Pose2d) {
        synchronized(lock) {
            try {
                driver.resetPosAndIMU()
            } catch (_: Exception) {}
        }
    }

    override fun updateInputs(inputs: OdometryInputs) {
        synchronized(lock) {
            inputs.posX = latestPosX
            inputs.posY = latestPosY
            inputs.heading = latestHeading
            inputs.velX = latestVelX
            inputs.velY = latestVelY
            inputs.headingVelocity = latestHeadingVelocity
            inputs.timestampMs = if (latestTimestamp != 0L) latestTimestamp else com.areslib.util.RobotClock.currentTimeMillis()
        }
    }

    override fun close() {
        running = false
        thread.interrupt()
    }
}

