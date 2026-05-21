package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureState
import com.areslib.state.SuperstructureMode
import com.areslib.control.ControlBarrierFunction
import com.areslib.control.CBFFilteredOutput

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
            is RobotAction.SetClimberExtension -> {
                state.copy(
                    climber = state.climber.copy(targetExtensionMeters = action.meters)
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
        }.let { enforceSafetyInterlocks(it) }
    }

    private val cbf = ControlBarrierFunction(
        m = 180.0, // 180 degrees per meter of extension (90 deg / 0.25 m max extension coerced to slope + clearance)
        c = 45.0,  // Minimum 45.0 deg deployment for any climber extension
        alpha = 5.0
    )
    private val cbfOutput = CBFFilteredOutput()

    private fun enforceSafetyInterlocks(state: SuperstructureState): SuperstructureState {
        synchronized(cbfOutput) {
            val activeC = if (state.climber.extensionMeters > 0.005 || state.climber.targetExtensionMeters > 0.005) 45.0 else 0.0
            cbf.filter(
                x1Target = state.intake.targetAngleDegrees,
                x2Target = state.climber.targetExtensionMeters,
                x1Current = state.intake.pivotAngleDegrees,
                x2Current = state.climber.extensionMeters,
                dtSeconds = 1.0 / cbf.alpha, // Bypass velocity-rate-limiting for static setpoint actions while strictly maintaining safe invariant set boundaries
                cOverride = activeC,
                outBuffer = cbfOutput
            )

            var finalIntake = state.intake
            var finalClimber = state.climber

            if (cbfOutput.x1Filtered != state.intake.targetAngleDegrees) {
                finalIntake = finalIntake.copy(
                    targetAngleDegrees = cbfOutput.x1Filtered,
                    isDeployed = cbfOutput.x1Filtered >= 45.0
                )
            }

            if (cbfOutput.x2Filtered != state.climber.targetExtensionMeters) {
                finalClimber = finalClimber.copy(
                    targetExtensionMeters = cbfOutput.x2Filtered
                )
            }

            if (finalIntake === state.intake && finalClimber === state.climber) {
                return state
            }
            return state.copy(intake = finalIntake, climber = finalClimber)
        }
    }
}
