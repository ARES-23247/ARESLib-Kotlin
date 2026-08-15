package com.areslib.codegen

import com.areslib.subsystem.InterlockComparison
import com.areslib.superstructure.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SuperstructureKotlinGeneratorTest {

    @Test
    fun testGenerateSuperstructureFiles() {
        val doc = SuperstructureDocument(
            superstructureId = "marvin-superstructure",
            displayName = "Marvin Superstructure",
            initialStateId = "STOW",
            states = listOf(
                SuperstructureStatePreset(
                    stateId = "STOW",
                    subsystemTargets = listOf(
                        SuperstructureSubsystemTarget("intake", "power", constantDoubleValue = 0.0),
                        SuperstructureSubsystemTarget("flywheel", "velocity", constantDoubleValue = 0.0),
                        SuperstructureSubsystemTarget("cowl", "angle", constantDoubleValue = 15.0)
                    )
                ),
                SuperstructureStatePreset(
                    stateId = "SHOOT_SPEAKER",
                    subsystemTargets = listOf(
                        SuperstructureSubsystemTarget("intake", "power", constantDoubleValue = 0.0),
                        SuperstructureSubsystemTarget(
                            "flywheel",
                            "velocity",
                            targetMode = SuperstructureTargetMode.DYNAMIC_LUT,
                            lutId = "distance-rpm",
                            lutInputSourceField = "vision.targetDistanceMeters"
                        ),
                        SuperstructureSubsystemTarget("cowl", "angle", constantDoubleValue = 35.0)
                    )
                )
            ),
            transitions = listOf(
                StateTransitionEdge(
                    transitionId = "stow-to-shoot",
                    sourceStateId = "STOW",
                    targetStateId = "SHOOT_SPEAKER",
                    triggerKind = TransitionTriggerKind.ACTION_REQUEST,
                    actionKey = "superstructure.shoot"
                )
            ),
            interlocks = listOf(
                SuperstructureInterlockRule(
                    ruleId = "arm-cowl-safety",
                    primarySubsystemId = "arm",
                    primaryFieldId = "position",
                    conditionComparison = InterlockComparison.LESS_THAN,
                    conditionThreshold = 0.20,
                    constrainedSubsystemId = "cowl",
                    constrainedFieldId = "angle",
                    clampMaximum = 25.0
                )
            ),
            luts = listOf(
                SuperstructureDynamicLut(
                    lutId = "distance-rpm",
                    displayName = "Distance to RPM",
                    controlPoints = listOf(
                        LutControlPoint(1.0, 3000.0),
                        LutControlPoint(3.0, 4500.0),
                        LutControlPoint(5.0, 5500.0)
                    )
                )
            )
        )

        val generatedFiles = SuperstructureKotlinGenerator.generate(
            document = doc,
            packageName = "com.areslib.frc.generated.superstructure",
            objectPrefix = "Generated"
        )

        assertEquals(2, generatedFiles.size)
        val stateFile = generatedFiles.find { it.relativePath.contains("State") }
        val reducerFile = generatedFiles.find { it.relativePath.contains("Reducer") }

        assertNotNull(stateFile)
        assertNotNull(reducerFile)

        assertTrue(stateFile!!.content.contains("enum class GeneratedSuperstructureStateId"))
        assertTrue(stateFile.content.contains("STOW,"))
        assertTrue(stateFile.content.contains("SHOOT_SPEAKER,"))
        assertTrue(stateFile.content.contains("val intakePower: Double"))
        assertTrue(stateFile.content.contains("val flywheelVelocity: Double"))
        assertTrue(stateFile.content.contains("val cowlAngle: Double"))

        assertTrue(reducerFile!!.content.contains("object GeneratedSuperstructureReducer"))
        assertTrue(reducerFile.content.contains("fun reduce("))
        assertTrue(reducerFile.content.contains("sampleLut_distanceRpm"))
        assertTrue(reducerFile.content.contains("raw_cowlAngle = kotlin.math.min(raw_cowlAngle, 25.0)"))
    }
}
