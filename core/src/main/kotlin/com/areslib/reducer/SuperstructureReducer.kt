package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureState

object SuperstructureReducer {
    /**
     * Reduces the generic SuperstructureState by merging dynamic SubsystemState updates.
     */
    fun reduce(state: SuperstructureState, action: RobotAction): SuperstructureState {
        return when (action) {
            is RobotAction.UpdateSubsystemState -> {
                // Generically merge the new sub-state into the states map
                val updatedStates = state.states + (action.state::class.java to action.state)
                state.copy(states = updatedStates)
            }
            else -> state
        }
    }
}
