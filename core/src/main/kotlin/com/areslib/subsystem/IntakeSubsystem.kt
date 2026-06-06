package com.areslib.subsystem

import com.areslib.action.RobotAction

class IntakeSubsystem(private val store: Store) {
    val isDeployed: Boolean
        get() = store.state.superstructure.intakeActive

    fun deploy() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(RobotAction.SetIntakeActive(active = true, timestampMs = timestamp))
    }

    fun retract() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(RobotAction.SetIntakeActive(active = false, timestampMs = timestamp))
    }
}
