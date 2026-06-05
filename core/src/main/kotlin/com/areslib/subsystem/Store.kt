package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer

class Store(
    initialState: RobotState = RobotState(),
    private val reducer: (RobotState, RobotAction) -> RobotState = ::rootReducer
) {
    var state: RobotState = initialState
        private set

    private val listeners = mutableListOf<(RobotState) -> Unit>()
    
    /**
     * Intercepts all dispatched actions (useful for telemetry logging and sequence recorders).
     */
    var actionListener: ((RobotAction) -> Unit)? = null

    fun dispatch(action: RobotAction) {
        actionListener?.invoke(action)
        state = reducer(state, action)
        listeners.forEach { it(state) }
    }

    fun subscribe(listener: (RobotState) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}
