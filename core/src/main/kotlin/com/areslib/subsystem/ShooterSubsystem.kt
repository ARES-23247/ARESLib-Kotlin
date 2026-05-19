package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureMode

class ShooterSubsystem(private val store: Store) {
    val mode: SuperstructureMode
        get() = store.state.superstructure.mode

    val flywheelRPM: Double
        get() = store.state.superstructure.flywheel.velocityRpm

    val flywheelTargetRPM: Double
        get() = store.state.superstructure.flywheel.targetVelocityRpm

    val cowlAngleDegrees: Double
        get() = store.state.superstructure.cowl.angleDegrees

    val transferActive: Boolean
        get() = store.state.superstructure.transferActive

    fun spinUp(targetRpm: Double) {
        val timestamp = System.currentTimeMillis()
        store.dispatch(RobotAction.SetFlywheelSpeed(targetRpm, timestamp))
        store.dispatch(RobotAction.SetFlywheelActive(true, timestamp))
    }

    fun shoot() {
        val timestamp = System.currentTimeMillis()
        store.dispatch(RobotAction.SetTransferActive(true, timestamp))
    }

    fun stop() {
        val timestamp = System.currentTimeMillis()
        store.dispatch(RobotAction.SetFlywheelActive(false, timestamp))
        store.dispatch(RobotAction.SetTransferActive(false, timestamp))
    }

    fun setCowlAngle(degrees: Double) {
        val timestamp = System.currentTimeMillis()
        store.dispatch(RobotAction.SetCowlAngle(degrees, timestamp))
    }
}
