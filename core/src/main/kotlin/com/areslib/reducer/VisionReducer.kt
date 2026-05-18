package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.VisionState

object VisionReducer {
    private const val MAX_VISION_BUFFER_SIZE = 50
    private const val MAX_AMBIGUITY = 0.2

    /**
     * Reduces the VisionState slice based on general vision updates and tag observations.
     */
    fun reduce(state: VisionState, action: RobotAction): VisionState {
        return when (action) {
            is RobotAction.VisionUpdate -> {
                state.copy(
                    hasTarget = action.hasTarget,
                    targetX = action.targetX,
                    targetY = action.targetY,
                    lastTargetTimestampMs = action.timestampMs
                )
            }
            is RobotAction.VisionMeasurementsReceived -> {
                // Filter out highly ambiguous measurements
                val validMeasurements = action.measurements.filter { it.ambiguity < MAX_AMBIGUITY }
                if (validMeasurements.isEmpty()) {
                    state
                } else {
                    // Keep last N measurements to avoid memory leak while retaining chronological history
                    val newMeasurements = (state.measurements + validMeasurements)
                        .takeLast(MAX_VISION_BUFFER_SIZE)

                    state.copy(
                        lastTargetTimestampMs = action.timestampMs,
                        hasTarget = true,
                        measurements = newMeasurements
                    )
                }
            }
            else -> state
        }
    }
}
