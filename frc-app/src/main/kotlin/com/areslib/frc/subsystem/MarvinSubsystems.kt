package com.areslib.frc.subsystem

import com.areslib.subsystem.Store
import com.areslib.state.SuperstructureMode
import com.areslib.frc.action.SetFlywheelSpeed
import com.areslib.frc.action.SetCowlAngle
import com.areslib.frc.action.SetIntakePivot
import com.areslib.frc.action.SetIntakeRollers
import com.areslib.frc.action.SetClimberVoltage
import com.areslib.frc.action.SetClimberExtension
import com.areslib.action.RobotAction

class MarvinShooterSubsystem(private val store: Store) {
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
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(SetFlywheelSpeed(targetRpm, timestamp))
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

    fun setCowlAngle(degrees: Double) {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(SetCowlAngle(degrees, timestamp))
    }
}

class MarvinIntakeSubsystem(private val store: Store) {
    val isDeployed: Boolean
        get() = store.state.superstructure.intake.isDeployed

    val pivotAngleDegrees: Double
        get() = store.state.superstructure.intake.pivotAngleDegrees

    val rollerSpeedRps: Double
        get() = store.state.superstructure.intake.rollerVelocityRps

    fun deploy() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(SetIntakePivot(deployed = true, timestampMs = timestamp))
    }

    fun retract() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(SetIntakePivot(deployed = false, timestampMs = timestamp))
    }

    fun setRollerSpeed(rps: Double) {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(SetIntakeRollers(speedRps = rps, timestampMs = timestamp))
    }
}

class MarvinClimberSubsystem(private val store: Store) {
    val extensionMeters: Double
        get() = store.state.superstructure.climber.extensionMeters

    val targetExtensionMeters: Double
        get() = store.state.superstructure.climber.targetExtensionMeters

    fun setTargetExtension(meters: Double) {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(SetClimberExtension(meters, timestamp))
    }

    fun setVoltage(volts: Double) {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        store.dispatch(SetClimberVoltage(volts, timestamp))
    }
}
