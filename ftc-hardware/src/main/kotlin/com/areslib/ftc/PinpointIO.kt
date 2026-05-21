package com.areslib.ftc

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.action.RobotAction

class PinpointIO(private val driver: GoBildaPinpointDriver) {
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
            lastX = driver.posX
            lastY = driver.posY
            lastHeading = driver.heading
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
}
