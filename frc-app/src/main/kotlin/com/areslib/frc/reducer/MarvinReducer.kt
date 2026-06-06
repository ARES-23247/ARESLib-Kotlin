package com.areslib.frc.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer
import com.areslib.frc.action.*
import com.areslib.frc.state.*
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

        // Then apply Marvin XIX specific state updates
        val currentMarvin = nextState.superstructure.marvinXIX
        val nextMarvin = when (action) {
            is SetFlywheelSpeed -> {
                currentMarvin.copy(
                    flywheel = currentMarvin.flywheel.copy(targetVelocityRpm = action.rpm)
                )
            }
            is SetCowlAngle -> {
                currentMarvin.copy(
                    cowl = currentMarvin.cowl.copy(targetAngleDegrees = action.degrees)
                )
            }
            is SetIntakePivot -> {
                currentMarvin.copy(
                    intake = currentMarvin.intake.copy(
                        isDeployed = action.deployed,
                        targetAngleDegrees = if (action.deployed) 90.0 else 0.0
                    )
                )
            }
            is SetIntakeRollers -> {
                currentMarvin.copy(
                    intake = currentMarvin.intake.copy(targetRollerVelocityRps = action.speedRps)
                )
            }
            is SetFeederSpeed -> {
                currentMarvin.copy(
                    feeder = currentMarvin.feeder.copy(targetVelocityRps = action.speedRps)
                )
            }
            is SetFloorSpeed -> {
                currentMarvin.copy(
                    floor = currentMarvin.floor.copy(targetVelocityRps = action.speedRps)
                )
            }
            is SetClimberVoltage -> {
                currentMarvin.copy(
                    climber = currentMarvin.climber.copy(targetVoltage = action.volts)
                )
            }
            is SetClimberExtension -> {
                currentMarvin.copy(
                    climber = currentMarvin.climber.copy(targetExtensionMeters = action.meters)
                )
            }
            is SuperstructureSensorUpdate -> {
                currentMarvin.copy(
                    flywheel = currentMarvin.flywheel.copy(velocityRpm = action.flywheelRpm),
                    cowl = currentMarvin.cowl.copy(angleDegrees = action.cowlAngle),
                    intake = currentMarvin.intake.copy(pivotAngleDegrees = action.intakeAngle),
                    feeder = currentMarvin.feeder.copy(gamePieceDetected = action.pieceDetected),
                    floor = currentMarvin.floor.copy(velocityRps = action.floorVelocityRps),
                    climber = currentMarvin.climber.copy(extensionMeters = action.climberExtensionMeters)
                )
            }
            else -> null
        }

        if (nextMarvin != null) {
            nextState = nextState.copy(
                superstructure = nextState.superstructure.copy(custom = nextMarvin)
            )
        }

        // Finally, enforce Marvin XIX specific safety interlocks (Control Barrier Function)
        return nextState.copy(
            superstructure = enforceSafetyInterlocks(nextState.superstructure)
        )
    }

    private fun enforceSafetyInterlocks(state: com.areslib.state.SuperstructureState): com.areslib.state.SuperstructureState {
        val marvin = state.marvinXIX
        synchronized(cbfOutput) {
            val activeC = if (marvin.climber.extensionMeters > 0.005 || marvin.climber.targetExtensionMeters > 0.005) 45.0 else 0.0
            cbf.filter(
                x1Target = marvin.intake.targetAngleDegrees,
                x2Target = marvin.climber.targetExtensionMeters,
                x1Current = marvin.intake.pivotAngleDegrees,
                x2Current = marvin.climber.extensionMeters,
                dtSeconds = 1.0 / cbf.alpha, // Bypass velocity-rate-limiting for static setpoint actions while strictly maintaining safe invariant set boundaries
                cOverride = activeC,
                outBuffer = cbfOutput
            )

            var finalIntake = marvin.intake
            var finalClimber = marvin.climber

            if (cbfOutput.x1Filtered != marvin.intake.targetAngleDegrees) {
                finalIntake = finalIntake.copy(
                    targetAngleDegrees = cbfOutput.x1Filtered,
                    isDeployed = cbfOutput.x1Filtered >= 45.0
                )
            }

            if (cbfOutput.x2Filtered != marvin.climber.targetExtensionMeters) {
                finalClimber = finalClimber.copy(
                    targetExtensionMeters = cbfOutput.x2Filtered
                )
            }

            if (finalIntake === marvin.intake && finalClimber === marvin.climber) {
                return state
            }
            val updatedMarvin = marvin.copy(intake = finalIntake, climber = finalClimber)
            return state.copy(custom = updatedMarvin)
        }
    }
}
