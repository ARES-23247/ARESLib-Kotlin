package com.areslib.hardware.vision

import com.areslib.action.RobotAction

class VisionHardware(
    private val io: VisionIO
) {
    private val inputs = VisionIOInputs()

    fun periodic(): RobotAction.VisionMeasurementsReceived? {
        io.updateInputs(inputs)

        if (inputs.measurements.isNotEmpty()) {
            val action = RobotAction.VisionMeasurementsReceived(
                measurements = inputs.measurements.toList(), // Defensive copy
                timestampMs = System.currentTimeMillis()
            )
            // Clear inputs after dispatch to prevent duplicate dispatches
            inputs.measurements = emptyList()
            return action
        }
        return null
    }
}
