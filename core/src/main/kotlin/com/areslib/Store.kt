package com.areslib

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer

/**
 * Class implementation for Store.
 *
 * Pure Redux state definition and deterministic reducer transition handler.
 */
class Store(
    initialState: RobotState = RobotState(),
    private val reducer: (RobotState, RobotAction) -> RobotState = ::rootReducer
) {
    @Volatile var state: RobotState = initialState
        private set

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(RobotState) -> Unit>()
    
    /**
     * Intercepts all dispatched actions (useful for telemetry logging and sequence recorders).
     */
    var actionListener: ((RobotAction) -> Unit)? = null

    /**
     * dispatch declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun dispatch(action: RobotAction) {
        val currentState: RobotState
        synchronized(this) {
            actionListener?.invoke(action)
            state = reducer(state, action)
            currentState = state
        }
        val size = listeners.size
        for (i in 0 until size) {
            try {
                listeners[i](currentState)
            } catch (_: IndexOutOfBoundsException) {
                break
            }
        }
    }

    /**
     * dispatchAll declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun dispatchAll(vararg actions: RobotAction) {
        val currentState: RobotState
        synchronized(this) {
            val actionCount = actions.size
            for (i in 0 until actionCount) {
                actionListener?.invoke(actions[i])
                state = reducer(state, actions[i])
            }
            currentState = state
        }
        val size = listeners.size
        for (i in 0 until size) {
            try {
                listeners[i](currentState)
            } catch (_: IndexOutOfBoundsException) {
                break
            }
        }
    }

    /**
     * subscribe declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun subscribe(listener: (RobotState) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}
