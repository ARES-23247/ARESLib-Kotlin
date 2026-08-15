package com.areslib.codegen

import com.areslib.subsystem.InterlockComparison
import com.areslib.superstructure.*

data class GeneratedSuperstructureFile(
    val relativePath: String,
    val content: String,
    val description: String = "",
)

object SuperstructureKotlinGenerator {

    fun generate(
        document: SuperstructureDocument,
        packageName: String,
        objectPrefix: String = "Generated"
    ): List<GeneratedSuperstructureFile> {
        val issues = validateSuperstructureDocument(document)
        val errors = issues.filter { it.severity == SuperstructureIssueSeverity.ERROR }
        require(errors.isEmpty()) { issues.joinToString("; ") { "${it.path}: ${it.message}" } }

        val stateFile = generateStateFile(document, packageName, objectPrefix)
        val reducerFile = generateReducerFile(document, packageName, objectPrefix)

        return listOf(stateFile, reducerFile)
    }

    private fun generateStateFile(
        document: SuperstructureDocument,
        packageName: String,
        objectPrefix: String
    ): GeneratedSuperstructureFile {
        val stateEnumName = "${objectPrefix}SuperstructureStateId"
        val stateDataName = "${objectPrefix}SuperstructureState"

        val allSubsystemTargets = document.states.flatMap { it.subsystemTargets }
            .distinctBy { "${it.subsystemId}_${it.fieldId}" }
            .sortedBy { "${it.subsystemId}_${it.fieldId}" }

        val source = buildString {
            appendLine("// ARES OWNERSHIP: GENERATED - DO NOT EDIT")
            appendLine("// Deterministic, zero-allocation superstructure state models.")
            appendLine("package $packageName")
            appendLine()
            appendLine("/** Declared state preset IDs for superstructure '${document.superstructureId}'. */")
            appendLine("enum class $stateEnumName {")
            document.states.forEach { state ->
                val id = state.stateId.toUpperSnakeCase()
                appendLine("    $id,")
            }
            appendLine("}")
            appendLine()
            appendLine("/** Immutable snapshot of the superstructure state and active subsystem targets. */")
            appendLine("data class $stateDataName(")
            appendLine("    val current: $stateEnumName = $stateEnumName.${document.initialStateId.toUpperSnakeCase()},")
            appendLine("    val previous: $stateEnumName = $stateEnumName.${document.initialStateId.toUpperSnakeCase()},")
            appendLine("    val stateEntryTimestampNanos: Long = 0L,")
            appendLine("    val isTransitioning: Boolean = false,")
            appendLine("    val isFaulted: Boolean = false,")
            appendLine("    val faultReason: String? = null,")

            allSubsystemTargets.forEach { target ->
                val propName = "${target.subsystemId.toCamelCase()}${target.fieldId.toPascalCase()}"
                val defaultVal = target.constantDoubleValue?.toString() ?: "0.0"
                appendLine("    val $propName: Double = $defaultVal,")
            }

            appendLine(") {")
            appendLine("    val currentStateId: String get() = current.name")
            appendLine("}")
        }

        return GeneratedSuperstructureFile(
            relativePath = "${stateDataName}.kt",
            content = source,
            description = "Immutable superstructure state shape and preset enum."
        )
    }

    private fun generateReducerFile(
        document: SuperstructureDocument,
        packageName: String,
        objectPrefix: String
    ): GeneratedSuperstructureFile {
        val stateEnumName = "${objectPrefix}SuperstructureStateId"
        val stateDataName = "${objectPrefix}SuperstructureState"
        val reducerName = "${objectPrefix}SuperstructureReducer"

        val allSubsystemTargets = document.states.flatMap { it.subsystemTargets }
            .distinctBy { "${it.subsystemId}_${it.fieldId}" }
            .sortedBy { "${it.subsystemId}_${it.fieldId}" }

        val source = buildString {
            appendLine("// ARES OWNERSHIP: GENERATED - DO NOT EDIT")
            appendLine("// Pure deterministic zero-allocation superstructure state reducer.")
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.areslib.superstructure.SuperstructureDocumentCodec")
            appendLine()
            appendLine("object $reducerName {")
            appendLine("    const val SUPERSTRUCTURE_ID: String = \"${document.superstructureId}\"")
            appendLine("    const val DOCUMENT_HASH: String = \"${SuperstructureDocumentCodec.contentHash(document)}\"")
            appendLine()
            appendLine("    /**")
            appendLine("     * Pure 0-allocation step function for the superstructure.")
            appendLine("     * Evaluates state presets, sensor conditions, dynamic LUTs, and collision interlocks.")
            appendLine("     */")
            appendLine("    fun reduce(")
            appendLine("        state: $stateDataName,")
            appendLine("        requestedState: $stateEnumName?,")
            appendLine("        sensorLookup: (String) -> Double,")
            appendLine("        nowNanos: Long,")
            appendLine("    ): $stateDataName {")
            appendLine("        // 1. Evaluate requested transition or automatic transitions")
            appendLine("        var nextState = state.current")
            appendLine("        var isTransitioning = state.isTransitioning")
            appendLine("        var entryNanos = state.stateEntryTimestampNanos")
            appendLine()
            appendLine("        if (requestedState != null && requestedState != state.current) {")
            appendLine("            nextState = requestedState")
            appendLine("            entryNanos = nowNanos")
            appendLine("            isTransitioning = true")
            appendLine("        } else {")
            appendLine("            // Automatic edge transitions")
            appendLine("            when (state.current) {")
            document.states.forEach { state ->
                val stateId = state.stateId.toUpperSnakeCase()
                val outgoingTransitions = document.transitions.filter { it.sourceStateId == state.stateId }
                val timeoutSecs = state.timeoutSeconds
                val timeoutTarget = state.timeoutTargetStateId
                val blockLines = mutableListOf<String>()
                if (timeoutSecs != null && timeoutTarget != null) {
                    val timeoutNanos = (timeoutSecs * 1e9).toLong()
                    val targetEnum = timeoutTarget.toUpperSnakeCase()
                    blockLines += "                    if (nowNanos - entryNanos >= ${timeoutNanos}L) {"
                    blockLines += "                        nextState = $stateEnumName.$targetEnum"
                    blockLines += "                        entryNanos = nowNanos"
                    blockLines += "                    }"
                }
                outgoingTransitions.filter { it.triggerKind == TransitionTriggerKind.SENSOR_CONDITION_AUTO }.forEach { edge ->
                    val targetEnum = edge.targetStateId.toUpperSnakeCase()
                    if (edge.guards.isNotEmpty()) {
                        val guardChecks = edge.guards.joinToString(" && ") { guard ->
                            val expected = guard.expectedDoubleValue ?: if (guard.expectedBooleanValue == true) 1.0 else 0.0
                            val field = guard.sourceField
                            when (guard.comparison) {
                                InterlockComparison.EQUALS_STATE -> "kotlin.math.abs(sensorLookup(\"$field\") - $expected) < ${guard.tolerance}"
                                InterlockComparison.NOT_EQUALS_STATE -> "kotlin.math.abs(sensorLookup(\"$field\") - $expected) >= ${guard.tolerance}"
                                InterlockComparison.LESS_THAN -> "sensorLookup(\"$field\") < $expected"
                                InterlockComparison.GREATER_THAN -> "sensorLookup(\"$field\") > $expected"
                            }
                        }
                        blockLines += "                    if ($guardChecks) {"
                        blockLines += "                        nextState = $stateEnumName.$targetEnum"
                        blockLines += "                        entryNanos = nowNanos"
                        blockLines += "                    }"
                    }
                }
                if (blockLines.isEmpty()) {
                    appendLine("                $stateEnumName.$stateId -> {}")
                } else {
                    appendLine("                $stateEnumName.$stateId -> {")
                    blockLines.forEach { appendLine(it) }
                    appendLine("                }")
                }
            }
            appendLine("            }")
            appendLine("        }")
            appendLine()
            appendLine("        // 2. Resolve raw subsystem targets for nextState")
            allSubsystemTargets.forEach { target ->
                val propName = "${target.subsystemId.toCamelCase()}${target.fieldId.toPascalCase()}"
                appendLine("        var raw_$propName = 0.0")
            }
            appendLine()
            appendLine("        when (nextState) {")
            document.states.forEach { state ->
                val stateId = state.stateId.toUpperSnakeCase()
                appendLine("            $stateEnumName.$stateId -> {")
                state.subsystemTargets.forEach { target ->
                    val propName = "${target.subsystemId.toCamelCase()}${target.fieldId.toPascalCase()}"
                    when (target.targetMode) {
                        SuperstructureTargetMode.CONSTANT -> {
                            val value = target.constantDoubleValue ?: (if (target.constantBooleanValue == true) 1.0 else 0.0)
                            appendLine("                raw_$propName = $value")
                        }
                        SuperstructureTargetMode.DYNAMIC_LUT -> {
                            val lut = document.luts.find { it.lutId == target.lutId }
                            val inputField = target.lutInputSourceField ?: ""
                            if (lut != null && lut.controlPoints.isNotEmpty()) {
                                appendLine("                raw_$propName = sampleLut_${lut.lutId.toCamelCase()}(sensorLookup(\"$inputField\"))")
                            } else {
                                appendLine("                raw_$propName = 0.0")
                            }
                        }
                        SuperstructureTargetMode.PASS_THROUGH -> {
                            appendLine("                raw_$propName = sensorLookup(\"${target.subsystemId}.${target.fieldId}\")")
                        }
                    }
                }
                appendLine("            }")
            }
            appendLine("        }")
            appendLine()
            appendLine("        // 3. Enforce multi-subsystem collision interlock clamps")
            document.interlocks.forEach { interlock ->
                val targetProp = "${interlock.constrainedSubsystemId.toCamelCase()}${interlock.constrainedFieldId.toPascalCase()}"
                val compOp = when (interlock.conditionComparison) {
                    InterlockComparison.LESS_THAN -> "<"
                    InterlockComparison.GREATER_THAN -> ">"
                    InterlockComparison.EQUALS_STATE -> "=="
                    InterlockComparison.NOT_EQUALS_STATE -> "!="
                }
                appendLine("        if (sensorLookup(\"${interlock.primarySubsystemId}.${interlock.primaryFieldId}\") $compOp ${interlock.conditionThreshold}) {")
                if (interlock.clampMinimum != null) {
                    appendLine("            raw_$targetProp = kotlin.math.max(raw_$targetProp, ${interlock.clampMinimum})")
                }
                if (interlock.clampMaximum != null) {
                    appendLine("            raw_$targetProp = kotlin.math.min(raw_$targetProp, ${interlock.clampMaximum})")
                }
                appendLine("        }")
            }
            appendLine()
            appendLine("        return state.copy(")
            appendLine("            current = nextState,")
            appendLine("            previous = state.current,")
            appendLine("            stateEntryTimestampNanos = entryNanos,")
            appendLine("            isTransitioning = isTransitioning && (nextState != state.current),")

            allSubsystemTargets.forEach { target ->
                val propName = "${target.subsystemId.toCamelCase()}${target.fieldId.toPascalCase()}"
                appendLine("            $propName = raw_$propName,")
            }

            appendLine("        )")
            appendLine("    }")
            appendLine()

            // Inlined LUT sampling methods
            document.luts.forEach { lut ->
                appendLine("    /** Inlined piece-wise linear interpolation for LUT '${lut.lutId}'. Zero-allocation. */")
                appendLine("    fun sampleLut_${lut.lutId.toCamelCase()}(x: Double): Double {")
                val pts = lut.controlPoints
                if (pts.isEmpty()) {
                    appendLine("        return 0.0")
                } else if (pts.size == 1) {
                    appendLine("        return ${pts.first().outputY}")
                } else {
                    appendLine("        if (x <= ${pts.first().inputX}) return ${pts.first().outputY}")
                    appendLine("        if (x >= ${pts.last().inputX}) return ${pts.last().outputY}")
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[i]
                        val p1 = pts[i + 1]
                        val dx = p1.inputX - p0.inputX
                        appendLine("        if (x <= ${p1.inputX}) {")
                        appendLine("            val t = (x - ${p0.inputX}) / $dx")
                        when (lut.interpolation) {
                            LutInterpolationMethod.STEP -> appendLine("            return ${p0.outputY}")
                            LutInterpolationMethod.LINEAR -> appendLine("            return ${p0.outputY} + t * (${p1.outputY} - ${p0.outputY})")
                            LutInterpolationMethod.SMOOTH_COSINE -> {
                                appendLine("            val factor = (1.0 - kotlin.math.cos(t * kotlin.math.PI)) / 2.0")
                                appendLine("            return ${p0.outputY} + factor * (${p1.outputY} - ${p0.outputY})")
                            }
                        }
                        appendLine("        }")
                    }
                    appendLine("        return ${pts.last().outputY}")
                }
                appendLine("    }")
                appendLine()
            }

            appendLine("}")
        }

        return GeneratedSuperstructureFile(
            relativePath = "${reducerName}.kt",
            content = source,
            description = "Pure deterministic superstructure reducer and inlined LUT solvers."
        )
    }

    private fun String.toUpperSnakeCase(): String = this.replace('-', '_').replace(' ', '_').uppercase()
    private fun String.toCamelCase(): String {
        val parts = this.split('-', '_', ' ')
        return parts.first().lowercase() + parts.drop(1).joinToString("") { it.capitalizeFirst() }
    }
    private fun String.toPascalCase(): String = this.split('-', '_', ' ').joinToString("") { it.capitalizeFirst() }
    private fun String.capitalizeFirst(): String = if (isEmpty()) "" else this[0].uppercaseChar() + substring(1)
}
