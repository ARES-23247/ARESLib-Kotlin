package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureState
import com.areslib.state.SuperstructureMode

object SuperstructureReducer {
    /**
     * Reduces the SuperstructureState slice based on intake, flywheel, and transfer actions.
     */
    fun reduce(state: SuperstructureState, action: RobotAction): SuperstructureState {
        return when (action) {
            is RobotAction.PathEventTriggered -> {
                if (action.eventName == "IntakeOn") {
                    state.copy(
                        intakeActive = true,
                        mode = SuperstructureMode.INTAKING
                    )
                } else if (action.eventName == "IntakeOff") {
                    state.copy(
                        intakeActive = false,
                        mode = if (state.flywheelActive) {
                            if (state.isFlywheelAtSpeed) SuperstructureMode.FLYWHEEL_READY
                            else SuperstructureMode.FLYWHEEL_SPINUP
                        } else SuperstructureMode.IDLE
                    )
                } else {
                    state
                }
            }
            is RobotAction.SetIntakeActive -> {
                val newMode = if (action.active) {
                    SuperstructureMode.INTAKING
                } else if (state.flywheelActive) {
                    if (state.isFlywheelAtSpeed) SuperstructureMode.FLYWHEEL_READY
                    else SuperstructureMode.FLYWHEEL_SPINUP
                } else {
                    SuperstructureMode.IDLE
                }
                state.copy(
                    intakeActive = action.active,
                    mode = newMode
                )
            }
            is RobotAction.SetFlywheelActive -> {
                val newMode = if (action.active) {
                    SuperstructureMode.FLYWHEEL_SPINUP
                } else {
                    if (state.intakeActive) SuperstructureMode.INTAKING
                    else SuperstructureMode.IDLE
                }
                state.copy(
                    flywheelActive = action.active,
                    transferActive = if (!action.active) false else state.transferActive,
                    mode = newMode
                )
            }
            is RobotAction.SetTransferActive -> {
                // Transfer only activates when flywheel is at speed and has game pieces
                val canTransfer = action.active && state.isFlywheelAtSpeed && state.inventoryCount > 0
                val newMode = if (canTransfer) {
                    SuperstructureMode.SHOOTING
                } else if (state.flywheelActive) {
                    if (state.isFlywheelAtSpeed) SuperstructureMode.FLYWHEEL_READY
                    else SuperstructureMode.FLYWHEEL_SPINUP
                } else if (state.intakeActive) {
                    SuperstructureMode.INTAKING
                } else {
                    SuperstructureMode.IDLE
                }
                state.copy(
                    transferActive = canTransfer,
                    mode = newMode
                )
            }
            is RobotAction.UpdateFlywheelRPM -> {
                val newMode = when {
                    state.transferActive && state.isFlywheelAtSpeed -> SuperstructureMode.SHOOTING
                    state.flywheelActive && action.rpm >= state.flywheelTargetRPM * 0.95 -> SuperstructureMode.FLYWHEEL_READY
                    state.flywheelActive -> SuperstructureMode.FLYWHEEL_SPINUP
                    state.intakeActive -> SuperstructureMode.INTAKING
                    else -> SuperstructureMode.IDLE
                }
                state.copy(
                    flywheelRPM = action.rpm,
                    mode = newMode
                )
            }
            is RobotAction.SetInventoryCount -> {
                state.copy(inventoryCount = action.count)
            }
            // FRC Subsystem Actions
            is RobotAction.SetFlywheelSpeed -> {
                state.copy(
                    flywheel = state.flywheel.copy(targetVelocityRpm = action.rpm)
                )
            }
            is RobotAction.SetCowlAngle -> {
                state.copy(
                    cowl = state.cowl.copy(targetAngleDegrees = action.degrees)
                )
            }
            is RobotAction.SetIntakePivot -> {
                state.copy(
                    intake = state.intake.copy(
                        isDeployed = action.deployed,
                        targetAngleDegrees = if (action.deployed) 90.0 else 0.0
                    )
                )
            }
            is RobotAction.SetIntakeRollers -> {
                state.copy(
                    intake = state.intake.copy(targetRollerVelocityRps = action.speedRps)
                )
            }
            is RobotAction.SetFeederSpeed -> {
                state.copy(
                    feeder = state.feeder.copy(targetVelocityRps = action.speedRps)
                )
            }
            is RobotAction.SetFloorSpeed -> {
                state.copy(
                    floor = state.floor.copy(targetVelocityRps = action.speedRps)
                )
            }
            is RobotAction.SetClimberVoltage -> {
                state.copy(
                    climber = state.climber.copy(targetVoltage = action.volts)
                )
            }
            is RobotAction.SuperstructureSensorUpdate -> {
                state.copy(
                    flywheel = state.flywheel.copy(velocityRpm = action.flywheelRpm),
                    cowl = state.cowl.copy(angleDegrees = action.cowlAngle),
                    intake = state.intake.copy(pivotAngleDegrees = action.intakeAngle),
                    feeder = state.feeder.copy(gamePieceDetected = action.pieceDetected),
                    floor = state.floor.copy(velocityRps = action.floorVelocityRps),
                    climber = state.climber.copy(extensionMeters = action.climberExtensionMeters)
                )
            }
            else -> state
        }
    }
}
