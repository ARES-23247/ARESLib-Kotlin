package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.VisionState

object VisionReducer {
    private const val MAX_VISION_BUFFER_SIZE = 50
    private const val MAX_AMBIGUITY = 0.2

    fun reduce(state: VisionState, action: RobotAction.VisionMeasurementsReceived): VisionState {
        // Filter out highly ambiguous measurements
        val validMeasurements = action.measurements.filter { it.ambiguity < MAX_AMBIGUITY }
        if (validMeasurements.isEmpty()) {
            return state
        }

        // Keep last N measurements to avoid memory leak while retaining chronological history
        val newMeasurements = (state.measurements + validMeasurements)
            .takeLast(MAX_VISION_BUFFER_SIZE)

        return state.copy(
            lastTargetTimestampMs = action.timestampMs,
            hasTarget = true,
            measurements = newMeasurements
        )
    }
}
