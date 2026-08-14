package com.areslib.behavior

import com.areslib.subsystem.InterlockComparison

/** Node type in the hierarchical behavior tree. */
enum class BehaviorNodeKind {
    /** Executes children sequentially; succeeds if all succeed, fails if any child fails. */
    SEQUENCE,
    /** Tries children sequentially; succeeds if any child succeeds, fails if all fail. */
    SELECTOR,
    /** Executes all children simultaneously; succeeds when all succeed. */
    PARALLEL,
    /** Evaluates a pure sensory state field condition against an expected threshold. */
    CONDITION,
    /** Dispatches a named subsystem action request. */
    ACTION,
    /** Waits for a specified duration in seconds. */
    WAIT,
}

/** Status returned by a behavior node execution. */
enum class BehaviorStatus {
    SUCCESS,
    FAILURE,
    RUNNING,
}

/**
 * One node in the declarative behavior tree graph.
 */
data class BehaviorNodeDocument(
    val nodeId: String,
    val kind: BehaviorNodeKind,
    val title: String = "",
    /** For [BehaviorNodeKind.CONDITION]: state field key to inspect. */
    val targetField: String? = null,
    /** For [BehaviorNodeKind.CONDITION]: comparison operator. */
    val comparison: InterlockComparison = InterlockComparison.EQUALS_STATE,
    val expectedDoubleValue: Double? = null,
    val expectedStringValue: String? = null,
    val expectedBooleanValue: Boolean? = null,
    /** For [BehaviorNodeKind.ACTION]: action identifier to dispatch. */
    val actionKey: String? = null,
    val actionParameters: Map<String, Double> = emptyMap(),
    /** For [BehaviorNodeKind.WAIT]: wait duration in seconds. */
    val waitDurationSeconds: Double? = null,
    /** Subordinate child nodes. */
    val children: List<BehaviorNodeDocument> = emptyList(),
)

/**
 * Canonical document representing a complete decision-making behavior tree.
 */
data class BehaviorTreeDocument(
    val treeId: String,
    val displayName: String,
    val rootNode: BehaviorNodeDocument,
    val description: String = "",
    val schemaVersion: Int = 1,
)
