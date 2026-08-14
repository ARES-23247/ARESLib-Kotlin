package com.areslib.behavior

import com.areslib.subsystem.InterlockComparison
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BehaviorTreeCompilerTest {

    @Test
    fun `selector chooses yellow scoring branch when color sensor reads yellow`() {
        val yellowBranch = BehaviorNodeDocument(
            nodeId = "seq_yellow",
            kind = BehaviorNodeKind.SEQUENCE,
            children = listOf(
                BehaviorNodeDocument(
                    nodeId = "cond_yellow",
                    kind = BehaviorNodeKind.CONDITION,
                    targetField = "intake.color",
                    comparison = InterlockComparison.EQUALS_STATE,
                    expectedStringValue = "YELLOW",
                ),
                BehaviorNodeDocument(
                    nodeId = "act_score_high",
                    kind = BehaviorNodeKind.ACTION,
                    actionKey = "elevator.high_basket",
                ),
            ),
        )

        val blueBranch = BehaviorNodeDocument(
            nodeId = "seq_blue",
            kind = BehaviorNodeKind.SEQUENCE,
            children = listOf(
                BehaviorNodeDocument(
                    nodeId = "cond_blue",
                    kind = BehaviorNodeKind.CONDITION,
                    targetField = "intake.color",
                    comparison = InterlockComparison.EQUALS_STATE,
                    expectedStringValue = "BLUE",
                ),
                BehaviorNodeDocument(
                    nodeId = "act_eject",
                    kind = BehaviorNodeKind.ACTION,
                    actionKey = "intake.reverse",
                ),
            ),
        )

        val rootSelector = BehaviorNodeDocument(
            nodeId = "root_selector",
            kind = BehaviorNodeKind.SELECTOR,
            children = listOf(yellowBranch, blueBranch),
        )

        val tree = BehaviorTreeDocument(
            treeId = "sample_sorting",
            displayName = "Sample Sorting Tree",
            rootNode = rootSelector,
        )

        val evaluator = BehaviorTreeEvaluator(tree)
        val dispatchedActions = mutableListOf<String>()

        // Test 1: State is YELLOW
        val statusYellow = evaluator.tick(
            stateLookup = { field -> if (field == "intake.color") "YELLOW" else null },
            actionDispatcher = { action, _ -> dispatchedActions.add(action) },
        )
        assertEquals(BehaviorStatus.SUCCESS, statusYellow)
        assertEquals(listOf("elevator.high_basket"), dispatchedActions)

        // Test 2: State is BLUE
        dispatchedActions.clear()
        val statusBlue = evaluator.tick(
            stateLookup = { field -> if (field == "intake.color") "BLUE" else null },
            actionDispatcher = { action, _ -> dispatchedActions.add(action) },
        )
        assertEquals(BehaviorStatus.SUCCESS, statusBlue)
        assertEquals(listOf("intake.reverse"), dispatchedActions)

        // Test 3: State is NONE (unmatched condition)
        dispatchedActions.clear()
        val statusNone = evaluator.tick(
            stateLookup = { field -> if (field == "intake.color") "NONE" else null },
            actionDispatcher = { action, _ -> dispatchedActions.add(action) },
        )
        assertEquals(BehaviorStatus.FAILURE, statusNone)
        assertTrue(dispatchedActions.isEmpty())
    }

    @Test
    fun `numeric threshold conditions accurately branch on sensor values`() {
        val distanceCheck = BehaviorNodeDocument(
            nodeId = "seq_distance",
            kind = BehaviorNodeKind.SEQUENCE,
            children = listOf(
                BehaviorNodeDocument(
                    nodeId = "cond_distance_close",
                    kind = BehaviorNodeKind.CONDITION,
                    targetField = "intake.distanceCm",
                    comparison = InterlockComparison.LESS_THAN,
                    expectedDoubleValue = 5.0,
                ),
                BehaviorNodeDocument(
                    nodeId = "act_grab",
                    kind = BehaviorNodeKind.ACTION,
                    actionKey = "claw.close",
                ),
            ),
        )

        val tree = BehaviorTreeDocument(
            treeId = "grab_piece",
            displayName = "Grab Piece",
            rootNode = distanceCheck,
        )

        val evaluator = BehaviorTreeEvaluator(tree)
        val actions = mutableListOf<String>()

        val success = evaluator.tick(
            stateLookup = { 3.2 },
            actionDispatcher = { a, _ -> actions.add(a) },
        )
        assertEquals(BehaviorStatus.SUCCESS, success)
        assertEquals(listOf("claw.close"), actions)

        actions.clear()
        val failure = evaluator.tick(
            stateLookup = { 8.5 },
            actionDispatcher = { a, _ -> actions.add(a) },
        )
        assertEquals(BehaviorStatus.FAILURE, failure)
        assertTrue(actions.isEmpty())
    }
}
