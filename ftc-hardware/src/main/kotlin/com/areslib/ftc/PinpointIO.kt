package com.areslib.ftc

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.action.RobotAction
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit

class PinpointIO(private val driver: GoBildaPinpointDriver) {
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetHeading = 0.0

    private var lastX = 0.0
    private var lastY = 0.0
    private var lastHeading = 0.0
    private var lastWarningTime = 0L

    /**
     * Updates the pinpoint driver and returns the current pose as a pure action.
     */
    fun getPoseUpdate(): RobotAction.PoseUpdate {
        try {
            driver.update()
            val rawX = driver.getPosX(DistanceUnit.METER)
            val rawY = driver.getPosY(DistanceUnit.METER)
            val rawHeading = driver.getHeading(AngleUnit.RADIANS)

            val cosH = kotlin.math.cos(offsetHeading)
            val sinH = kotlin.math.sin(offsetHeading)
            lastX = rawX * cosH - rawY * sinH + offsetX
            lastY = rawX * sinH + rawY * cosH + offsetY
            lastHeading = com.areslib.math.InputMath.wrapAngle(rawHeading + offsetHeading)
        } catch (e: Exception) {
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            if (now - lastWarningTime > 2000L) {
                System.err.println("PinpointIO: Communication failure with GoBildaPinpointDriver. Using last known coordinates. Error: ${e.message}")
                lastWarningTime = now
            }
        }
        
        return RobotAction.PoseUpdate(
            xMeters = lastX,
            yMeters = lastY,
            headingRadians = lastHeading,
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        )
    }

    /**
     * Resets the pinpoint computer and recalibrates the orientation.
     * Optionally configures starting pose tracking values.
     */
    @kotlin.jvm.JvmOverloads
    fun initialize(pose: com.areslib.math.Pose2d = com.areslib.math.Pose2d(), resetHardware: Boolean = false) {
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
        } catch (_: Exception) {}
    }
}
