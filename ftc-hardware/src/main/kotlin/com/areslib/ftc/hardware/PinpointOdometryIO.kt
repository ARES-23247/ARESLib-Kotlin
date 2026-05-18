package com.areslib.ftc.hardware

import com.areslib.hardware.OdometryIO
import com.areslib.hardware.OdometryInputs
import com.areslib.math.Pose2d

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
    }

    override fun updateInputs(inputs: OdometryInputs) {
        driver.update()
        inputs.posX = driver.posX
        inputs.posY = driver.posY
        inputs.heading = driver.heading
        inputs.velX = driver.velX
        inputs.velY = driver.velY
        inputs.headingVelocity = driver.headingVelocity
        inputs.timestampMs = System.currentTimeMillis()
    }
}
