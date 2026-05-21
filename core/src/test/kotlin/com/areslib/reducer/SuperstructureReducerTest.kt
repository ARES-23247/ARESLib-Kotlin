package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuperstructureReducerTest {

    @Test
    fun `test SetClimberExtension action updates climber target`() {
        val initialState = SuperstructureState()
        
        // 1. If intake is stowed (pivotAngleDegrees = 0.0), target extension should be clamped to 0.0
        val statePivotStowed = SuperstructureReducer.reduce(
            initialState,
            RobotAction.SetClimberExtension(0.25, 1000L)
        )
        assertEquals(0.0, statePivotStowed.climber.targetExtensionMeters, "Climber extension must be clamped to 0.0 when pivot is stowed")

        // 2. If intake is deployed (pivotAngleDegrees = 90.0), target extension should be set correctly
        val statePivotDeployed = SuperstructureState().copy(
            intake = SuperstructureState().intake.copy(pivotAngleDegrees = 90.0, targetAngleDegrees = 90.0)
        )
        val statePivotDeployedUpdated = SuperstructureReducer.reduce(
            statePivotDeployed,
            RobotAction.SetClimberExtension(0.25, 1000L)
        )
        assertEquals(0.25, statePivotDeployedUpdated.climber.targetExtensionMeters, "Climber extension should set correctly when pivot is deployed")
    }

    @Test
    fun `test coordinated interlocks reciprocal safety boundaries`() {
        // If climber target or physical extension > 0.02, intake pivot target angle must be >= 45.0
        val statePivotStowed = SuperstructureState().copy(
            intake = SuperstructureState().intake.copy(pivotAngleDegrees = 0.0, targetAngleDegrees = 0.0)
        )

        // When climber is physically extended (sensor update)
        val stateExtendedSensor = SuperstructureReducer.reduce(
            statePivotStowed,
            RobotAction.SuperstructureSensorUpdate(
                flywheelRpm = 0.0,
                cowlAngle = 0.0,
                intakeAngle = 0.0,
                pieceDetected = false,
                climberExtensionMeters = 0.03,
                timestampMs = 1000L
            )
        )
        assertEquals(45.0, stateExtendedSensor.intake.targetAngleDegrees, "Intake pivot target must be forced to 45.0 when climber is physically extended")
        assertTrue(stateExtendedSensor.intake.isDeployed)

        // When climber target is extended
        val statePivotDeployed = SuperstructureState().copy(
            intake = SuperstructureState().intake.copy(pivotAngleDegrees = 90.0, targetAngleDegrees = 90.0)
        )
        val stateClimberTargetExtended = SuperstructureReducer.reduce(
            statePivotDeployed,
            RobotAction.SetClimberExtension(0.1, 1000L)
        )
        // Now if we try to command intake pivot stowed (SetIntakePivot(false) -> targetAngleDegrees = 0.0)
        val statePivotStowAction = SuperstructureReducer.reduce(
            stateClimberTargetExtended,
            RobotAction.SetIntakePivot(deployed = false, 1100L)
        )
        assertEquals(45.0, statePivotStowAction.intake.targetAngleDegrees, "Intake pivot target must be clamped to 45.0 when climber is commanded extended")
        assertTrue(statePivotStowAction.intake.isDeployed)
    }
}
