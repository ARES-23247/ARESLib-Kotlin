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
    yDirection: GoBildaPinpointDriver.EncoderDirection = GoBildaPinpointDriver.EncoderDirection.FORWARD,
    private val isHeadingCcwPositive: Boolean = false
) : AutoCloseable {
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetHeading = 0.0

    init {
        driver.setOffsets(xOffsetMm, yOffsetMm, org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.MM)
        if (encoderResolution != null) {
            driver.setEncoderResolution(encoderResolution, DistanceUnit.MM)
        }
        driver.setEncoderDirections(xDirection, yDirection)
    }

    private var lastWarningTime = 0L

    private var lastX = 0.0
    private var lastY = 0.0
    private var lastHeading = 0.0
    private var lastHeadingVelocity = 0.0
    private var lastVelX = 0.0
    private var lastVelY = 0.0
    private var lastTimestampMs = 0L

    init {
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)
    }

    /**
     * Updates the pinpoint driver and returns the current pose as a pure action.
     */
    fun getPoseUpdate(): RobotAction.PoseUpdate {
        try {
            driver.update()
            val rawX = driver.getPosX(DistanceUnit.METER)
            val rawY = driver.getPosY(DistanceUnit.METER)
            val headingMult = if (isHeadingCcwPositive) 1.0 else -1.0
            val rawHeading = headingMult * driver.getHeading(AngleUnit.RADIANS)
            val rawHeadingVelocity = headingMult * driver.getHeadingVelocity(org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit.RADIANS)

            val cosH = kotlin.math.cos(offsetHeading)
            val sinH = kotlin.math.sin(offsetHeading)
            val x = rawX * cosH - rawY * sinH + offsetX
            val y = rawX * sinH + rawY * cosH + offsetY
            val nextHeading = wrapAngle(rawHeading + offsetHeading)

            lastX = x
            lastY = y
            lastHeading = nextHeading
            lastHeadingVelocity = rawHeadingVelocity
            lastVelX = driver.getVelX(DistanceUnit.METER)
            lastVelY = driver.getVelY(DistanceUnit.METER)
            lastTimestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        } catch (e: Exception) {
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            if (now - lastWarningTime > 2000L) {
                System.err.println("PinpointIO: Communication failure with GoBildaPinpointDriver. Using last known coordinates. Error: ${e.message}")
                lastWarningTime = now
            }
        }

        var ts = lastTimestampMs
        if (ts == 0L) ts = com.areslib.util.RobotClock.currentTimeMillis()

        return RobotAction.PoseUpdate(
            xMeters = lastX,
            yMeters = lastY,
            headingRadians = lastHeading,
            angularVelocityRadiansPerSecond = lastHeadingVelocity,
            timestampMs = ts,
            xVelocityMetersPerSecond = lastVelX,
            yVelocityMetersPerSecond = lastVelY
        )
    }

    /**
     * Recalibrates the internal IMU while the robot is stationary.
     */
    fun recalibrateIMU() {
        try {
            driver.recalibrateIMU()
        } catch (_: Exception) {}
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
                offsetX = pose.x
                offsetY = pose.y
                offsetHeading = pose.heading.radians
                lastX = pose.x
                lastY = pose.y
                lastHeading = pose.heading.radians
                lastHeadingVelocity = 0.0
                lastTimestampMs = com.areslib.util.RobotClock.currentTimeMillis()
            } else {
                driver.update()
                val rawX = driver.getPosX(DistanceUnit.METER)
                val rawY = driver.getPosY(DistanceUnit.METER)
                val headingMult = if (isHeadingCcwPositive) 1.0 else -1.0
                val rawHeading = headingMult * driver.getHeading(AngleUnit.RADIANS)

                offsetHeading = wrapAngle(pose.heading.radians - rawHeading)
                val cosH = kotlin.math.cos(offsetHeading)
                val sinH = kotlin.math.sin(offsetHeading)
                
                offsetX = pose.x - (rawX * cosH - rawY * sinH)
                offsetY = pose.y - (rawX * sinH + rawY * cosH)
                
                lastX = pose.x
                lastY = pose.y
                lastHeading = pose.heading.radians
            }
        } catch (_: Exception) {}
    }

    fun setOffsets(xOffsetMm: Double, yOffsetMm: Double) {
        try {
            // GoBilda Pinpoint setOffsets expects:
            // 1st arg: X-pod offset (sideways distance from tracking center, i.e., yOffsetMm)
            // 2nd arg: Y-pod offset (forward distance from tracking center, i.e., xOffsetMm)
            driver.setOffsets(yOffsetMm, xOffsetMm, DistanceUnit.MM)
        } catch (_: Exception) {}
    }

    fun setEncoderResolution(resolution: Double) {
        try {
            if (resolution > 0.0) {
                driver.setEncoderResolution(resolution, DistanceUnit.MM)
            }
        } catch (_: Exception) {}
    }

    override fun close() {
    }
}
