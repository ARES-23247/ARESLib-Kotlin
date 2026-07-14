package com.areslib.ftc.drivetrain

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.action.RobotAction
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.areslib.math.wrapAngle

/**
 * Interface to the GoBilda Pinpoint Odometry Computer.
 * 
 * COORDINATE SYSTEM:
 * - Position: X = forward (audience wall), Y = left (blue alliance)
 * - Heading: 0° = +X (audience wall), CCW-positive (math standard)
 * - The raw GoBilda Pinpoint outputs CW-positive heading;
 *   we negate it here at the hardware boundary so all downstream
 *   consumers (EKF, kinematics, path followers) receive CCW-positive.
 */
class PinpointIO @kotlin.jvm.JvmOverloads constructor(
    private val driver: GoBildaPinpointDriver,
    xOffsetMm: Double = 0.0,
    yOffsetMm: Double = 0.0,
    encoderResolution: Double? = null,
    xDirection: GoBildaPinpointDriver.EncoderDirection = GoBildaPinpointDriver.EncoderDirection.FORWARD,
    yDirection: GoBildaPinpointDriver.EncoderDirection = GoBildaPinpointDriver.EncoderDirection.FORWARD
) : AutoCloseable {
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetHeading = 0.0

    init {
        driver.setOffsets(xOffsetMm, yOffsetMm, org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.MM)
        if (encoderResolution != null) {
            driver.setEncoderResolution(encoderResolution)
        }
        driver.setEncoderDirections(xDirection, yDirection)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lock = Any()
    private var running = true

    private var lastX = 0.0
    private var lastY = 0.0
    private var lastHeading = 0.0
    private var lastTimestampMs = 0L
    private var lastWarningTime = 0L

    private val thread = Thread {
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
                val heading = wrapAngle(rawHeading + offsetHeading)

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
    }.apply {
        isDaemon = true
        name = "ARES-Pinpoint-Thread"
    }

    init {
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)
        thread.start()
    }

    /**
     * Updates the pinpoint driver and returns the current pose as a pure action.
     */
    fun getPoseUpdate(): RobotAction.PoseUpdate {
        val x: Double
        val y: Double
        val heading: Double
        var ts: Long
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
    fun initialize(pose: com.areslib.math.geometry.Pose2d = com.areslib.math.geometry.Pose2d(), resetHardware: Boolean = false) {
        try {
            if (resetHardware) {
                driver.resetPosAndIMU()
                synchronized(lock) {
                    offsetX = pose.x
                    offsetY = pose.y
                    offsetHeading = pose.heading.radians
                    lastX = pose.x
                    lastY = pose.y
                    lastHeading = pose.heading.radians
                    lastTimestampMs = com.areslib.util.RobotClock.currentTimeMillis()
                }
            } else {
                driver.update()
                val rawX = driver.getPosX(DistanceUnit.METER)
                val rawY = driver.getPosY(DistanceUnit.METER)
                val rawHeading = driver.getHeading(AngleUnit.RADIANS)

                synchronized(lock) {
                    offsetHeading = wrapAngle(pose.heading.radians - rawHeading)
                    val cosH = kotlin.math.cos(offsetHeading)
                    val sinH = kotlin.math.sin(offsetHeading)
                    offsetX = pose.x - (rawX * cosH - rawY * sinH)
                    offsetY = pose.y - (rawX * sinH + rawY * cosH)
                    lastX = pose.x
                    lastY = pose.y
                    lastHeading = pose.heading.radians
                    lastTimestampMs = com.areslib.util.RobotClock.currentTimeMillis()
                }
            }
        } catch (_: Exception) {}
    }

    override fun close() {
        running = false
        thread.interrupt()
        scope.cancel()
    }
}

