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
                val index = state.states.indexOfFirst { it::class.java == action.state::class.java }
                val newStates = if (index != -1) {
                    val list = ArrayList<com.areslib.state.SubsystemState>(state.states)
                    list[index] = action.state
                    list
                } else {
                    val list = ArrayList<com.areslib.state.SubsystemState>(state.states.size + 1)
                    list.addAll(state.states)
                    list.add(action.state)
                    list
                }
                state.copy(states = newStates)
            }
            else -> state
        }
    }
}
