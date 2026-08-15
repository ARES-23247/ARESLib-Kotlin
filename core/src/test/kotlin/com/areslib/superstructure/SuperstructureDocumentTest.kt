package com.areslib.superstructure

import com.areslib.subsystem.InterlockComparison
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SuperstructureDocumentTest {

    @Test
    fun testDynamicLutSampling() {
        val lut = SuperstructureDynamicLut(
            lutId = "distance-to-rpm",
            displayName = "Distance to RPM",
            inputUnit = "m",
            outputUnit = "RPM",
            interpolation = LutInterpolationMethod.LINEAR,
            controlPoints = listOf(
                LutControlPoint(1.0, 3000.0),
                LutControlPoint(2.0, 4000.0),
                LutControlPoint(4.0, 6000.0)
            )
        )

        // Lower and upper clamp
        assertEquals(3000.0, lut.sample(0.5), 1e-6)
        assertEquals(6000.0, lut.sample(5.0), 1e-6)

        // Exact control points
        assertEquals(3000.0, lut.sample(1.0), 1e-6)
        assertEquals(4000.0, lut.sample(2.0), 1e-6)
        assertEquals(6000.0, lut.sample(4.0), 1e-6)

        // Linear interpolation
        assertEquals(3500.0, lut.sample(1.5), 1e-6)
        assertEquals(5000.0, lut.sample(3.0), 1e-6)

        // Smooth cosine interpolation
        val smoothLut = lut.copy(interpolation = LutInterpolationMethod.SMOOTH_COSINE)
        assertEquals(3500.0, smoothLut.sample(1.5), 1e-6) // midpoint of symmetric cosine is 0.5
    }

    @Test
    fun testCodecRoundTripAndValidation() {
        val doc = SuperstructureDocument(
            superstructureId = "main-superstructure",
            displayName = "Main Superstructure",
            description = "Coordinated intake, indexer, flywheel, and cowl",
            initialStateId = "STOW",
            states = listOf(
                SuperstructureStatePreset(
                    stateId = "STOW",
                    displayName = "Stowed",
                    description = "Mechanism within perimeter",
                    subsystemTargets = listOf(
                        SuperstructureSubsystemTarget("intake", "power", constantDoubleValue = 0.0),
                        SuperstructureSubsystemTarget("flywheel", "velocity", constantDoubleValue = 0.0),
                        SuperstructureSubsystemTarget("cowl", "angle", constantDoubleValue = 15.0)
                    )
                ),
                SuperstructureStatePreset(
                    stateId = "INTAKE",
                    displayName = "Intaking",
                    subsystemTargets = listOf(
                        SuperstructureSubsystemTarget("intake", "power", constantDoubleValue = 1.0),
                        SuperstructureSubsystemTarget("flywheel", "velocity", constantDoubleValue = 0.0),
                        SuperstructureSubsystemTarget("cowl", "angle", constantDoubleValue = 15.0)
                    )
                ),
                SuperstructureStatePreset(
                    stateId = "SPINUP",
                    displayName = "Flywheel Spinup",
                    subsystemTargets = listOf(
                        SuperstructureSubsystemTarget("intake", "power", constantDoubleValue = 0.0),
                        SuperstructureSubsystemTarget(
                            "flywheel",
                            "velocity",
                            targetMode = SuperstructureTargetMode.DYNAMIC_LUT,
                            lutId = "speaker-rpm",
                            lutInputSourceField = "vision.distanceMeters"
                        ),
                        SuperstructureSubsystemTarget("cowl", "angle", constantDoubleValue = 35.0)
                    )
                )
            ),
            transitions = listOf(
                StateTransitionEdge(
                    transitionId = "stow-to-intake",
                    sourceStateId = "STOW",
                    targetStateId = "INTAKE",
                    triggerKind = TransitionTriggerKind.ACTION_REQUEST,
                    actionKey = "superstructure.intake"
                ),
                StateTransitionEdge(
                    transitionId = "intake-to-spinup",
                    sourceStateId = "INTAKE",
                    targetStateId = "SPINUP",
                    triggerKind = TransitionTriggerKind.SENSOR_CONDITION_AUTO,
                    guards = listOf(
                        TransitionGuard(
                            guardId = "beam-break-tripped",
                            sourceField = "sensors.beamBreak",
                            comparison = InterlockComparison.EQUALS_STATE,
                            expectedBooleanValue = true
                        )
                    ),
                    debounceMs = 50L
                )
            ),
            interlocks = listOf(
                SuperstructureInterlockRule(
                    ruleId = "elevator-arm-clearance",
                    description = "Arm must stay below 20 deg if elevator is low",
                    primarySubsystemId = "elevator",
                    primaryFieldId = "position",
                    conditionComparison = InterlockComparison.LESS_THAN,
                    conditionThreshold = 0.10,
                    constrainedSubsystemId = "arm",
                    constrainedFieldId = "angle",
                    clampMaximum = 20.0
                )
            ),
            luts = listOf(
                SuperstructureDynamicLut(
                    lutId = "speaker-rpm",
                    displayName = "Speaker RPM Curve",
                    inputUnit = "m",
                    outputUnit = "RPM",
                    controlPoints = listOf(
                        LutControlPoint(1.0, 3200.0),
                        LutControlPoint(3.0, 4800.0),
                        LutControlPoint(5.0, 5800.0)
                    )
                )
            )
        )

        val json = SuperstructureDocumentCodec.encode(doc)
        assertNotNull(json)
        val decoded = SuperstructureDocumentCodec.decode(json)
        assertEquals(doc, decoded)

        val hash = SuperstructureDocumentCodec.contentHash(doc)
        assertTrue(hash.length == 64)
    }

    @Test
    fun testValidationCatchesInvalidStates() {
        val invalidDoc = SuperstructureDocument(
            superstructureId = "invalid-doc",
            initialStateId = "NON_EXISTENT",
            states = listOf(
                SuperstructureStatePreset(stateId = "STOW")
            )
        )
        val issues = validateSuperstructureDocument(invalidDoc)
        assertTrue(issues.any { it.severity == SuperstructureIssueSeverity.ERROR && it.path == "initialStateId" })

        assertThrows<IllegalArgumentException> {
            SuperstructureDocumentCodec.encode(invalidDoc)
        }
    }
}
