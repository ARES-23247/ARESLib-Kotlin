package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureMode

class ShooterSubsystem(private val store: Store) {
    val mode: SuperstructureMode
        get() = store.state.superstructure.mode

    val flywheelRPM: Double
        get() = store.state.superstructure.flywheelRPM

    val flywheelTargetRPM: Double
        get() = store.state.superstructure.flywheelTargetRPM

    val transferActive: Boolean
        get() = store.state.superstructure.transferActive

    fun spinUp(targetRpm: Double) {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(RobotAction.SetFlywheelTargetRPM(targetRpm, timestamp))
        store.dispatch(RobotAction.SetFlywheelActive(true, timestamp))
    }

    fun shoot() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(RobotAction.SetTransferActive(true, timestamp))
    }

    fun stop() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(RobotAction.SetFlywheelActive(false, timestamp))
        store.dispatch(RobotAction.SetTransferActive(false, timestamp))
    }
}
