package com.areslib.behavior

import com.areslib.subsystem.InterlockComparison

/**
 * Pure non-blocking runtime evaluator for declarative [BehaviorTreeDocument] structures.
 *
 * Evaluates dynamic conditions, branch selections, and action dispatches on the robot control loop
 * without blocking threads or allocating heap garbage during steady-state ticks.
 */
class BehaviorTreeEvaluator(val document: BehaviorTreeDocument) {

    /**
     * Executes one tick of the behavior tree against the current robot state snapshot.
     *
     * @param stateLookup Function resolving a state field key to its current typed value.
     * @param actionDispatcher Callback invoked when an [BehaviorNodeKind.ACTION] node fires.
     * @return [BehaviorStatus] result of the root node execution.
     */
    fun tick(
        stateLookup: (String) -> Any?,
        actionDispatcher: (String, Map<String, Double>) -> Unit = { _, _ -> },
    ): BehaviorStatus {
        return evaluateNode(document.rootNode, stateLookup, actionDispatcher)
    }

    private fun evaluateNode(
        node: BehaviorNodeDocument,
        stateLookup: (String) -> Any?,
        actionDispatcher: (String, Map<String, Double>) -> Unit,
    ): BehaviorStatus {
        return when (node.kind) {
            BehaviorNodeKind.CONDITION -> evaluateCondition(node, stateLookup)
            BehaviorNodeKind.ACTION -> {
                val key = node.actionKey
                if (!key.isNullOrBlank()) {
                    actionDispatcher(key, node.actionParameters)
                    BehaviorStatus.SUCCESS
                } else {
                    BehaviorStatus.FAILURE
                }
            }
            BehaviorNodeKind.SEQUENCE -> {
                for (child in node.children) {
                    val status = evaluateNode(child, stateLookup, actionDispatcher)
                    if (status != BehaviorStatus.SUCCESS) {
                        return status
                    }
                }
                BehaviorStatus.SUCCESS
            }
            BehaviorNodeKind.SELECTOR -> {
                for (child in node.children) {
                    val status = evaluateNode(child, stateLookup, actionDispatcher)
                    if (status == BehaviorStatus.SUCCESS || status == BehaviorStatus.RUNNING) {
                        return status
                    }
                }
                BehaviorStatus.FAILURE
            }
            BehaviorNodeKind.PARALLEL -> {
                var anyRunning = false
                for (child in node.children) {
                    val status = evaluateNode(child, stateLookup, actionDispatcher)
                    if (status == BehaviorStatus.FAILURE) return BehaviorStatus.FAILURE
                    if (status == BehaviorStatus.RUNNING) anyRunning = true
                }
                if (anyRunning) BehaviorStatus.RUNNING else BehaviorStatus.SUCCESS
            }
            BehaviorNodeKind.WAIT -> BehaviorStatus.SUCCESS
        }
    }

    private fun evaluateCondition(
        node: BehaviorNodeDocument,
        stateLookup: (String) -> Any?,
    ): BehaviorStatus {
        val field = node.targetField ?: return BehaviorStatus.FAILURE
        val currentVal = stateLookup(field) ?: return BehaviorStatus.FAILURE

        val matches = when (node.comparison) {
            InterlockComparison.EQUALS_STATE -> {
                when {
                    node.expectedBooleanValue != null -> currentVal == node.expectedBooleanValue
                    node.expectedStringValue != null -> currentVal.toString() == node.expectedStringValue
                    node.expectedDoubleValue != null -> {
                        val num = (currentVal as? Number)?.toDouble() ?: return BehaviorStatus.FAILURE
                        kotlin.math.abs(num - node.expectedDoubleValue) < 1e-4
                    }
                    else -> false
                }
            }
            InterlockComparison.NOT_EQUALS_STATE -> {
                when {
                    node.expectedBooleanValue != null -> currentVal != node.expectedBooleanValue
                    node.expectedStringValue != null -> currentVal.toString() != node.expectedStringValue
                    node.expectedDoubleValue != null -> {
                        val num = (currentVal as? Number)?.toDouble() ?: return BehaviorStatus.FAILURE
                        kotlin.math.abs(num - node.expectedDoubleValue) >= 1e-4
                    }
                    else -> true
                }
            }
            InterlockComparison.LESS_THAN -> {
                val num = (currentVal as? Number)?.toDouble() ?: return BehaviorStatus.FAILURE
                val target = node.expectedDoubleValue ?: return BehaviorStatus.FAILURE
                num < target
            }
            InterlockComparison.GREATER_THAN -> {
                val num = (currentVal as? Number)?.toDouble() ?: return BehaviorStatus.FAILURE
                val target = node.expectedDoubleValue ?: return BehaviorStatus.FAILURE
                num > target
            }
        }

        return if (matches) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE
    }
}
