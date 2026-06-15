package com.areslib.ftc

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.action.RobotAction
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit

class PinpointIO(private val driver: GoBildaPinpointDriver) {
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetHeading = 0.0

    private val lock = Any()
    private var running = true

    private var lastX = 0.0
    private var lastY = 0.0
    private var lastHeading = 0.0
    private var lastTimestampMs = 0L
    private var lastWarningTime = 0L

    init {
        val thread = Thread {
            while (running) {
                try {
                    driver.update()
                    val rawX = driver.getPosX(DistanceUnit.METER)
                    val rawY = driver.getPosY(DistanceUnit.METER)
                    val rawHeading = driver.getHeading(AngleUnit.RADIANS)

                    val cosH = kotlin.math.cos(offsetHeading)
                    val sinH = kotlin.math.sin(offsetHeading)
                    val x = rawX * cosH - rawY * sinH + offsetX
                    val y = rawX * sinH + rawY * cosH + offsetY
                    val heading = com.areslib.math.InputMath.wrapAngle(rawHeading + offsetHeading)

                    synchronized(lock) {
                        lastX = x
                        lastY = y
                        lastHeading = heading
                        lastTimestampMs = com.areslib.util.RobotClock.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    val now = com.areslib.util.RobotClock.currentTimeMillis()
                    if (now - lastWarningTime > 2000L) {
                        System.err.println("PinpointIO: Communication failure with GoBildaPinpointDriver. Using last known coordinates. Error: ${e.message}")
                        lastWarningTime = now
                    }
                }
                try { Thread.sleep(5) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }
        }
        thread.isDaemon = true
        thread.name = "ARES-Pinpoint-Thread"
        thread.start()
    }

    /**
     * Updates the pinpoint driver and returns the current pose as a pure action.
     */
    fun getPoseUpdate(): RobotAction.PoseUpdate {
        var x = 0.0
        var y = 0.0
        var heading = 0.0
        var ts = 0L
        synchronized(lock) {
            x = lastX
            y = lastY
            heading = lastHeading
            ts = lastTimestampMs
        }
        if (ts == 0L) ts = com.areslib.util.RobotClock.currentTimeMillis()
        return RobotAction.PoseUpdate(
            xMeters = x,
            yMeters = y,
            headingRadians = heading,
            timestampMs = ts
        )
    }

    /**
     * Resets the pinpoint computer and recalibrates the orientation.
     * Optionally configures starting pose tracking values.
     */
    @kotlin.jvm.JvmOverloads
    fun initialize(pose: com.areslib.math.Pose2d = com.areslib.math.Pose2d(), resetHardware: Boolean = false) {
        synchronized(lock) {
            try {
                if (resetHardware) {
                    driver.resetPosAndIMU()
                    offsetX = pose.x
                    offsetY = pose.y
                    offsetHeading = pose.heading.radians
                } else {
                    driver.update()
                    val rawX = driver.getPosX(DistanceUnit.METER)
                    val rawY = driver.getPosY(DistanceUnit.METER)
                    val rawHeading = driver.getHeading(AngleUnit.RADIANS)

                    offsetHeading = com.areslib.math.InputMath.wrapAngle(pose.heading.radians - rawHeading)
                    val cosH = kotlin.math.cos(offsetHeading)
                    val sinH = kotlin.math.sin(offsetHeading)
                    offsetX = pose.x - (rawX * cosH - rawY * sinH)
                    offsetY = pose.y - (rawX * sinH + rawY * cosH)
                }
                lastX = pose.x
                lastY = pose.y
                lastHeading = pose.heading.radians
                lastTimestampMs = com.areslib.util.RobotClock.currentTimeMillis()
            } catch (_: Exception) {}
        }
    }

    fun close() {
        running = false
    }
}
