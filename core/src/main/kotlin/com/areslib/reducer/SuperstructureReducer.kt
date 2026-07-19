package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureState

object SuperstructureReducer {
    /**
     * Reduces the generic SuperstructureState by merging dynamic SubsystemState updates.
     */
    fun reduce(state: SuperstructureState, action: RobotAction): SuperstructureState {
        return when (action) {
            is RobotAction.UpdateSuperstructure -> {
                state.copy(
                    intakeActive = action.intakeActive ?: state.intakeActive,
                    flywheelActive = action.flywheelActive ?: state.flywheelActive,
                    flywheelTargetRPM = action.flywheelTargetRPM ?: state.flywheelTargetRPM
                )
            }
            is RobotAction.SetIndicatorLight -> {
                state.copy(
                    indicatorLights = state.indicatorLights + (action.name to action.position)
                )
            }
            else -> state
        }
    }
}
