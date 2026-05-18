package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.PathState

object PathReducer {
    /**
     * Reduces the PathState slice based on path events, switching, and progress updates.
     */
    fun reduce(state: PathState, action: RobotAction): PathState {
        return when (action) {
            is RobotAction.SwitchPath -> {
                val backupPath = if (action.isDetour) {
                    state.originalPathBeforeDetour ?: state.activePath
                } else {
                    null
                }
                state.copy(
                    activePath = action.path,
                    currentDistanceMeters = 0.0,
                    detourActive = action.isDetour,
                    originalPathBeforeDetour = backupPath
                )
            }
            is RobotAction.UpdatePathProgress -> {
                state.copy(
                    currentDistanceMeters = action.distanceProgressMeters
                )
            }
            else -> state
        }
    }
}
