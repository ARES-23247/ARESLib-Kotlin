package com.areslib.frc.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer
import com.areslib.frc.action.*
import com.areslib.control.ControlBarrierFunction
import com.areslib.control.CBFFilteredOutput

object MarvinReducer {
    private val cbf = ControlBarrierFunction(
        m = 180.0, // 180 degrees per meter of extension (90 deg / 0.25 m max extension coerced to slope + clearance)
        c = 45.0,  // Minimum 45.0 deg deployment for any climber extension
        alpha = 5.0
    )
    private val cbfOutput = CBFFilteredOutput()

    fun reduce(state: RobotState, action: RobotAction): RobotState {
        // First run standard core reducer (handles drive, vision, path, costmap, and generic FSM)
        var nextState = rootReducer(state, action)

        // Then apply Marvin 19 specific state updates
        val nextSuperstructure = when (action) {
            is SetFlywheelSpeed -> {
                nextState.superstructure.copy(
                    flywheel = nextState.superstructure.flywheel.copy(targetVelocityRpm = action.rpm)
                )
            }
            is SetCowlAngle -> {
                nextState.superstructure.copy(
                    cowl = nextState.superstructure.cowl.copy(targetAngleDegrees = action.degrees)
                )
            }
            is SetIntakePivot -> {
                nextState.superstructure.copy(
                    intake = nextState.superstructure.intake.copy(
                        isDeployed = action.deployed,
                        targetAngleDegrees = if (action.deployed) 90.0 else 0.0
                    )
                )
            }
            is SetIntakeRollers -> {
                nextState.superstructure.copy(
                    intake = nextState.superstructure.intake.copy(targetRollerVelocityRps = action.speedRps)
                )
            }
            is SetFeederSpeed -> {
                nextState.superstructure.copy(
                    feeder = nextState.superstructure.feeder.copy(targetVelocityRps = action.speedRps)
                )
            }
            is SetFloorSpeed -> {
                nextState.superstructure.copy(
                    floor = nextState.superstructure.floor.copy(targetVelocityRps = action.speedRps)
                )
            }
            is SetClimberVoltage -> {
                nextState.superstructure.copy(
                    climber = nextState.superstructure.climber.copy(targetVoltage = action.volts)
                )
            }
            is SetClimberExtension -> {
                nextState.superstructure.copy(
                    climber = nextState.superstructure.climber.copy(targetExtensionMeters = action.meters)
                )
            }
            is SuperstructureSensorUpdate -> {
                nextState.superstructure.copy(
                    flywheel = nextState.superstructure.flywheel.copy(velocityRpm = action.flywheelRpm),
                    cowl = nextState.superstructure.cowl.copy(angleDegrees = action.cowlAngle),
                    intake = nextState.superstructure.intake.copy(pivotAngleDegrees = action.intakeAngle),
                    feeder = nextState.superstructure.feeder.copy(gamePieceDetected = action.pieceDetected),
                    floor = nextState.superstructure.floor.copy(velocityRps = action.floorVelocityRps),
                    climber = nextState.superstructure.climber.copy(extensionMeters = action.climberExtensionMeters)
                )
            }
            else -> null
        }

        if (nextSuperstructure != null) {
            nextState = nextState.copy(superstructure = nextSuperstructure)
        }

        // Finally, enforce Marvin 19 specific safety interlocks (Control Barrier Function)
        return nextState.copy(
            superstructure = enforceSafetyInterlocks(nextState.superstructure)
        )
    }

    private fun enforceSafetyInterlocks(state: com.areslib.state.SuperstructureState): com.areslib.state.SuperstructureState {
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
