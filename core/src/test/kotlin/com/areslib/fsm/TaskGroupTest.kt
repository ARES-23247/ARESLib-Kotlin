package com.areslib.fsm

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class MockTestTask(
    override val name: String,
    private val durationMs: Long,
    private val actionToDispatch: RobotAction? = null
) : Task {
    var initialized = false
    var ended = false
    var interrupted = false

    override fun initialize(state: RobotState): List<RobotAction> {
        initialized = true
        return actionToDispatch?.let { listOf(it) } ?: emptyList()
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        return elapsedMs >= durationMs
    }

    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {
        ended = true
        this.interrupted = interrupted
        return emptyList()
    }
}

class TaskGroupTest {

    @Test
    fun `test SequentialTaskGroup execution order`() {
        val action1 = RobotAction.SetInventoryCount(1, 100L)
        val action2 = RobotAction.SetInventoryCount(2, 200L)
        
        val task1 = MockTestTask("Task1", 100L, action1)
        val task2 = MockTestTask("Task2", 150L, action2)
        val sequence = SequentialTaskGroup(listOf(task1, task2))

        val state = RobotState()
        
        // Initialize sequence: task1 should initialize
        val initActions = sequence.initialize(state)
        assertTrue(task1.initialized)
        assertFalse(task2.initialized)
        assertEquals(1, initActions.size)
        assertEquals(action1, initActions[0])

        // First tick at 50ms: task1 not completed
        assertFalse(sequence.isCompleted(state, 50L))
        
        // Second tick at 100ms: task1 completed, triggers task1 end and task2 initialize
        assertFalse(sequence.isCompleted(state, 100L))
        
        assertTrue(task1.ended)
        assertTrue(task2.initialized)
        
        // The execute call immediately following should return the pending actions (action2)
        val execActions = sequence.execute(state, 100L)
        assertEquals(1, execActions.size)
        assertEquals(action2, execActions[0])

        // Third tick at 200ms: task2 elapsed is 200 - 100 = 100ms (not yet completed)
        assertFalse(sequence.isCompleted(state, 200L))
        
        // Fourth tick at 250ms: task2 elapsed is 250 - 100 = 150ms (completed)
        assertTrue(sequence.isCompleted(state, 250L))
        assertTrue(task2.ended)
    }

    @Test
    fun `test ParallelTaskGroup concurrent execution`() {
        val action1 = RobotAction.SetInventoryCount(1, 100L)
        val action2 = RobotAction.SetInventoryCount(2, 200L)

        val task1 = MockTestTask("Task1", 100L, action1)
        val task2 = MockTestTask("Task2", 200L, action2)
        val parallel = ParallelTaskGroup(listOf(task1, task2))

        val state = RobotState()

        // Initialize: both child tasks initialize
        val initActions = parallel.initialize(state)
        assertTrue(task1.initialized)
        assertTrue(task2.initialized)
        assertEquals(2, initActions.size)

        // At 50ms: neither completed
        assertFalse(parallel.isCompleted(state, 50L))

        // At 100ms: task1 completed, task2 not completed
        assertFalse(parallel.isCompleted(state, 100L))
        assertTrue(task1.ended)
        assertFalse(task2.ended)

        // At 200ms: task2 completed, parallel completed
        assertTrue(parallel.isCompleted(state, 200L))
        assertTrue(task2.ended)
    }
}
