package com.areslib.superstructure

import com.areslib.subsystem.InterlockComparison
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.security.MessageDigest

const val ARES_SUPERSTRUCTURE_SCHEMA_VERSION: Int = 1

enum class SuperstructureTargetMode {
    CONSTANT,
    DYNAMIC_LUT,
    PASS_THROUGH,
}

enum class TransitionTriggerKind {
    ACTION_REQUEST,
    SENSOR_CONDITION_AUTO,
    TIME_ELAPSED,
    DRIVER_COMMAND,
}

enum class LutInterpolationMethod {
    LINEAR,
    STEP,
    SMOOTH_COSINE,
}

data class LutControlPoint(
    val inputX: Double,
    val outputY: Double,
)

data class SuperstructureDynamicLut(
    val lutId: String,
    val displayName: String = "",
    val inputUnit: String = "",
    val outputUnit: String = "",
    val interpolation: LutInterpolationMethod = LutInterpolationMethod.LINEAR,
    val controlPoints: List<LutControlPoint> = emptyList(),
) {
    fun sample(x: Double): Double {
        if (controlPoints.isEmpty()) return 0.0
        if (controlPoints.size == 1 || x <= controlPoints.first().inputX) return controlPoints.first().outputY
        if (x >= controlPoints.last().inputX) return controlPoints.last().outputY

        for (i in 0 until controlPoints.size - 1) {
            val p0 = controlPoints[i]
            val p1 = controlPoints[i + 1]
            if (x >= p0.inputX && x <= p1.inputX) {
                val dx = p1.inputX - p0.inputX
                if (dx <= 1e-9) return p0.outputY
                val t = (x - p0.inputX) / dx
                return when (interpolation) {
                    LutInterpolationMethod.STEP -> p0.outputY
                    LutInterpolationMethod.LINEAR -> p0.outputY + t * (p1.outputY - p0.outputY)
                    LutInterpolationMethod.SMOOTH_COSINE -> {
                        val factor = (1.0 - kotlin.math.cos(t * kotlin.math.PI)) / 2.0
                        p0.outputY + factor * (p1.outputY - p0.outputY)
                    }
                }
            }
        }
        return controlPoints.last().outputY
    }
}

data class TransitionGuard(
    val guardId: String,
    val sourceField: String,
    val comparison: InterlockComparison = InterlockComparison.EQUALS_STATE,
    val expectedDoubleValue: Double? = null,
    val expectedBooleanValue: Boolean? = null,
    val expectedStringValue: String? = null,
    val tolerance: Double = 1e-4,
)

data class StateTransitionEdge(
    val transitionId: String,
    val sourceStateId: String,
    val targetStateId: String,
    val triggerKind: TransitionTriggerKind = TransitionTriggerKind.ACTION_REQUEST,
    val actionKey: String? = null,
    val guards: List<TransitionGuard> = emptyList(),
    val debounceMs: Long = 0L,
    val timeoutSeconds: Double? = null,
    val timeoutFallbackStateId: String? = null,
)

data class SuperstructureSubsystemTarget(
    val subsystemId: String,
    val fieldId: String,
    val targetMode: SuperstructureTargetMode = SuperstructureTargetMode.CONSTANT,
    val constantDoubleValue: Double? = null,
    val constantBooleanValue: Boolean? = null,
    val constantStringValue: String? = null,
    val lutId: String? = null,
    val lutInputSourceField: String? = null,
)

data class SuperstructureStatePreset(
    val stateId: String,
    val displayName: String = "",
    val description: String = "",
    val subsystemTargets: List<SuperstructureSubsystemTarget> = emptyList(),
    val timeoutSeconds: Double? = null,
    val timeoutTargetStateId: String? = null,
)

data class SuperstructureInterlockRule(
    val ruleId: String,
    val description: String = "",
    val primarySubsystemId: String,
    val primaryFieldId: String,
    val conditionComparison: InterlockComparison = InterlockComparison.LESS_THAN,
    val conditionThreshold: Double = 0.0,
    val constrainedSubsystemId: String,
    val constrainedFieldId: String,
    val clampMinimum: Double? = null,
    val clampMaximum: Double? = null,
    val enforceDisabled: Boolean = false,
)

data class SuperstructureDocument(
    val superstructureId: String,
    val displayName: String = "",
    val description: String = "",
    val schemaVersion: Int = ARES_SUPERSTRUCTURE_SCHEMA_VERSION,
    val initialStateId: String,
    val states: List<SuperstructureStatePreset> = emptyList(),
    val transitions: List<StateTransitionEdge> = emptyList(),
    val interlocks: List<SuperstructureInterlockRule> = emptyList(),
    val luts: List<SuperstructureDynamicLut> = emptyList(),
    val faultStateId: String? = null,
)

enum class SuperstructureIssueSeverity { ERROR, WARNING }

data class SuperstructureValidationIssue(
    val severity: SuperstructureIssueSeverity,
    val path: String,
    val message: String,
)

fun validateSuperstructureDocument(document: SuperstructureDocument): List<SuperstructureValidationIssue> {
    val issues = mutableListOf<SuperstructureValidationIssue>()
    if (document.superstructureId.isBlank()) {
        issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "superstructureId", "Superstructure ID must not be blank")
    }
    if (document.schemaVersion != ARES_SUPERSTRUCTURE_SCHEMA_VERSION) {
        issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "schemaVersion", "Unsupported schema version ${document.schemaVersion}")
    }
    if (document.states.isEmpty()) {
        issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "states", "Superstructure must declare at least one state preset")
    }

    val stateIds = document.states.map { it.stateId }.toSet()
    if (document.states.size != stateIds.size) {
        issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "states", "Duplicate state IDs found in state presets")
    }

    if (document.initialStateId.isBlank() || !stateIds.contains(document.initialStateId)) {
        issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "initialStateId", "Initial state '${document.initialStateId}' is not declared in states")
    }

    if (document.faultStateId != null && !stateIds.contains(document.faultStateId)) {
        issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "faultStateId", "Fault state '${document.faultStateId}' is not declared in states")
    }

    val lutMap = document.luts.associateBy { it.lutId }
    if (document.luts.size != lutMap.size) {
        issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "luts", "Duplicate LUT IDs found in superstructure dynamic LUTs")
    }

    document.luts.forEach { lut ->
        if (lut.lutId.isBlank()) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "luts[${lut.lutId}]", "LUT ID must not be blank")
        }
        if (lut.controlPoints.size < 2) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "luts[${lut.lutId}].controlPoints", "Dynamic LUT '${lut.lutId}' must have at least 2 control points")
        }
        for (i in 0 until lut.controlPoints.size - 1) {
            val curr = lut.controlPoints[i]
            val next = lut.controlPoints[i + 1]
            if (curr.inputX >= next.inputX) {
                issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "luts[${lut.lutId}].controlPoints", "Control point inputX values must be strictly increasing (${curr.inputX} >= ${next.inputX})")
            }
        }
    }

    document.states.forEach { state ->
        if (state.stateId.isBlank()) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "states[${state.stateId}]", "State preset ID must not be blank")
        }
        if (state.timeoutTargetStateId != null && !stateIds.contains(state.timeoutTargetStateId)) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "states[${state.stateId}].timeoutTargetStateId", "Timeout target state '${state.timeoutTargetStateId}' is not declared")
        }
        state.subsystemTargets.forEach { target ->
            if (target.subsystemId.isBlank() || target.fieldId.isBlank()) {
                issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "states[${state.stateId}].subsystemTargets", "Subsystem ID and field ID must not be blank")
            }
            if (target.targetMode == SuperstructureTargetMode.DYNAMIC_LUT) {
                if (target.lutId == null || !lutMap.containsKey(target.lutId)) {
                    issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "states[${state.stateId}].subsystemTargets[${target.subsystemId}]", "Referenced LUT '${target.lutId}' does not exist")
                }
                if (target.lutInputSourceField.isNullOrBlank()) {
                    issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "states[${state.stateId}].subsystemTargets[${target.subsystemId}]", "Dynamic LUT target requires a valid lutInputSourceField")
                }
            }
        }
    }

    document.transitions.forEach { transition ->
        if (transition.transitionId.isBlank()) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "transitions[${transition.transitionId}]", "Transition ID must not be blank")
        }
        if (!stateIds.contains(transition.sourceStateId)) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "transitions[${transition.transitionId}].sourceStateId", "Source state '${transition.sourceStateId}' does not exist")
        }
        if (!stateIds.contains(transition.targetStateId)) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "transitions[${transition.transitionId}].targetStateId", "Target state '${transition.targetStateId}' does not exist")
        }
        if (transition.timeoutFallbackStateId != null && !stateIds.contains(transition.timeoutFallbackStateId)) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "transitions[${transition.transitionId}].timeoutFallbackStateId", "Timeout fallback state '${transition.timeoutFallbackStateId}' does not exist")
        }
    }

    document.interlocks.forEach { interlock ->
        if (interlock.ruleId.isBlank()) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "interlocks[${interlock.ruleId}]", "Interlock rule ID must not be blank")
        }
        if (interlock.primarySubsystemId.isBlank() || interlock.primaryFieldId.isBlank()) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "interlocks[${interlock.ruleId}]", "Primary subsystem and field IDs must not be blank")
        }
        if (interlock.constrainedSubsystemId.isBlank() || interlock.constrainedFieldId.isBlank()) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.ERROR, "interlocks[${interlock.ruleId}]", "Constrained subsystem and field IDs must not be blank")
        }
        if (interlock.clampMinimum == null && interlock.clampMaximum == null) {
            issues += SuperstructureValidationIssue(SuperstructureIssueSeverity.WARNING, "interlocks[${interlock.ruleId}]", "Interlock declares no minimum or maximum clamp")
        }
    }

    return issues
}

object SuperstructureDocumentCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(document: SuperstructureDocument): String {
        requireValid(document)
        return gson.toJson(document)
    }

    fun decode(json: String): SuperstructureDocument {
        val document = try {
            val root = JsonParser.parseString(json).asJsonObject
            val schemaVersion = root.get("schemaVersion")?.asInt ?: ARES_SUPERSTRUCTURE_SCHEMA_VERSION
            require(schemaVersion == ARES_SUPERSTRUCTURE_SCHEMA_VERSION) {
                "Unsupported superstructure schema version $schemaVersion"
            }
            val parsed = gson.fromJson(json, SuperstructureDocument::class.java)
                ?: throw IllegalArgumentException("Superstructure document is empty")
            parsed
        } catch (error: Exception) {
            throw IllegalArgumentException("Superstructure document is not valid JSON: ${error.message}", error)
        }
        requireValid(document)
        return document
    }

    fun contentHash(document: SuperstructureDocument): String = MessageDigest.getInstance("SHA-256")
        .digest(encode(document).toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun requireValid(document: SuperstructureDocument) {
        val issues = validateSuperstructureDocument(document)
        val errors = issues.filter { it.severity == SuperstructureIssueSeverity.ERROR }
        require(errors.isEmpty()) { errors.joinToString("; ") { "${it.path}: ${it.message}" } }
    }
}
