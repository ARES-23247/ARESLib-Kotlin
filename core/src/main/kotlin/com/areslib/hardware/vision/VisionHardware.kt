package com.areslib.hardware.vision

import com.areslib.action.RobotAction

/**
 * Class implementation for Vision Hardware.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class VisionHardware(
    private val io: VisionIO
) {
    private val inputs = VisionIOInputs()

    /**
     * periodic declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun periodic(): RobotAction.VisionMeasurementsReceived? {
        io.updateInputs(inputs)

        if (inputs.measurements.isNotEmpty()) {
            val action = RobotAction.VisionMeasurementsReceived(
                measurements = inputs.measurements.toList(), // Defensive copy
                timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
            )
            // Clear inputs after dispatch to prevent duplicate dispatches
            inputs.measurements = emptyList()
            return action
        }
        return null
    }
}
