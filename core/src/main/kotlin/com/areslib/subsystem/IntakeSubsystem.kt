package com.areslib.subsystem

import com.areslib.action.RobotAction

class IntakeSubsystem(private val store: Store) {
    val isDeployed: Boolean
        get() = store.state.superstructure.intake.isDeployed

    val pivotAngleDegrees: Double
        get() = store.state.superstructure.intake.pivotAngleDegrees

    val rollerSpeedRps: Double
        get() = store.state.superstructure.intake.rollerVelocityRps

    fun deploy() {
        val timestamp = System.currentTimeMillis()
        store.dispatch(RobotAction.SetIntakePivot(deployed = true, timestampMs = timestamp))
    }

    fun retract() {
        val timestamp = System.currentTimeMillis()
        store.dispatch(RobotAction.SetIntakePivot(deployed = false, timestampMs = timestamp))
    }

    fun setRollerSpeed(rps: Double) {
        val timestamp = System.currentTimeMillis()
        store.dispatch(RobotAction.SetIntakeRollers(speedRps = rps, timestampMs = timestamp))
    }
}
