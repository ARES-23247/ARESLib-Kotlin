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

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(RobotState) -> Unit>()
    
    /**
     * Intercepts all dispatched actions (useful for telemetry logging and sequence recorders).
     */
    var actionListener: ((RobotAction) -> Unit)? = null

    fun dispatch(action: RobotAction) {
        actionListener?.invoke(action)
        state = reducer(state, action)
        val size = listeners.size
        for (i in 0 until size) {
            try {
                listeners[i](state)
            } catch (_: IndexOutOfBoundsException) {
                break
            }
        }
    }

    fun dispatchAll(vararg actions: RobotAction) {
        val actionCount = actions.size
        for (i in 0 until actionCount) {
            actionListener?.invoke(actions[i])
            state = reducer(state, actions[i])
        }
        val size = listeners.size
        for (i in 0 until size) {
            try {
                listeners[i](state)
            } catch (_: IndexOutOfBoundsException) {
                break
            }
        }
    }

    fun subscribe(listener: (RobotState) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}
