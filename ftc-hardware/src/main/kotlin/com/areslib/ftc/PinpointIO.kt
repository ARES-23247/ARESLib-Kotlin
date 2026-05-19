package com.areslib.ftc

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.action.RobotAction

class PinpointIO(private val driver: GoBildaPinpointDriver) {
    /**
     * Updates the pinpoint driver and returns the current pose as a pure action.
     */
    fun getPoseUpdate(): RobotAction.PoseUpdate {
        driver.update()
        // Assuming the driver provides data in mm or similar, we scale it to meters here
        // For the mock, we assume it provides meters natively.
        return RobotAction.PoseUpdate(
            xMeters = driver.posX,
            yMeters = driver.posY,
            headingRadians = driver.heading,
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        )
    }
}
