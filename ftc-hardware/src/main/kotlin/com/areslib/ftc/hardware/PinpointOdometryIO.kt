package com.areslib.ftc.hardware

import com.areslib.hardware.OdometryIO
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.qualcomm.robotcore.hardware.HardwareMap

// Normally we would import the actual driver here, but for now we assume it exists in the classpath
// import org.firstinspires.ftc.teamcode.GoBildaPinpointDriver

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

class PinpointOdometryIO(private val driver: PinpointDriverProxy) : OdometryIO {
    override fun initialize(startPose: Pose2d) {
        driver.resetPosAndIMU()
        // Driver does not inherently support setting a custom start pose easily without an offset,
        // so we assume standard reset for this interface.
    }

    override fun update() {
        driver.update()
    }

    override val position: Pose2d
        get() {
            // Assume driver outputs millimeters, convert to meters
            return Pose2d(
                x = driver.posX / 1000.0,
                y = driver.posY / 1000.0,
                heading = Rotation2d(driver.heading)
            )
        }

    override val velocity: Pose2d
        get() {
            return Pose2d(
                x = driver.velX / 1000.0,
                y = driver.velY / 1000.0,
                heading = Rotation2d(driver.headingVelocity)
            )
        }
}
