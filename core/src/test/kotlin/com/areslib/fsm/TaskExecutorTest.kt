package com.areslib.fsm

import com.areslib.action.RobotAction
import com.areslib.reducer.rootReducer
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskExecutorTest {

    @Test
    fun `test sequential tasks and time waiting`() {
        val executor = TaskExecutor()
        var state = RobotState()
        val baseTime = 1000L

        // Queue a dispatch, a time wait, and another dispatch
        val action1 = RobotAction.SetInventoryCount(5, baseTime)
        val action2 = RobotAction.SetInventoryCount(10, baseTime + 1000)

        executor.addTask(ActionDispatchTask(action1))
        executor.addTask(TimeWaitTask(500L))
        executor.addTask(ActionDispatchTask(action2))

        // Initial size
        assertEquals(3, executor.size())

        // First update dispatches action1 and starts TimeWaitTask immediately due to zero-latency cascade
        val actions1 = executor.update(state, baseTime)
        assertEquals(1, actions1.size)
        assertTrue(actions1[0] is RobotAction.SetInventoryCount)
        assertEquals(5, (actions1[0] as RobotAction.SetInventoryCount).count)

        // Apply action to state
        state = rootReducer(state, actions1[0])
        assertEquals(5, state.superstructure.inventoryCount)
        assertEquals("TimeWait(500 ms)", executor.getActiveTaskName())

        // Update before 500ms has elapsed — no action dispatched, wait task remains active
        val actions2 = executor.update(state, baseTime + 200L)
        assertTrue(actions2.isEmpty())
        assertEquals("TimeWait(500 ms)", executor.getActiveTaskName())

        // Update after 500ms has elapsed — wait task finishes and cascades to ActionDispatchTask(action2) immediately
        val actions3 = executor.update(state, baseTime + 500L)
        assertEquals(1, actions3.size)
        assertTrue(actions3[0] is RobotAction.SetInventoryCount)
        assertEquals(10, (actions3[0] as RobotAction.SetInventoryCount).count)

        state = rootReducer(state, actions3[0])
        assertEquals(10, state.superstructure.inventoryCount)
        assertNull(executor.getActiveTaskName())
        assertEquals(0, executor.size())
    }

    @Test
    fun `test complex conditional superstructure tasks`() {
        val executor = TaskExecutor()
        var state = RobotState()
        val baseTime = 1000L

        // 1. Queue FlywheelReadyTask (target 4000.0 RPM)
        // 2. Queue ShootTask
        executor.addTask(FlywheelReadyTask(4000.0, baseTime))
        executor.addTask(ShootTask(baseTime + 500L))

        // First update starts FlywheelReadyTask
        val actions1 = executor.update(state, baseTime)
        assertEquals(1, actions1.size)
        assertTrue(actions1[0] is RobotAction.SetFlywheelActive)
        assertTrue((actions1[0] as RobotAction.SetFlywheelActive).active)

        state = rootReducer(state, actions1[0])
        assertTrue(state.superstructure.flywheelActive)
        assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, state.superstructure.mode)

        // Simulate flywheel ramping up but not ready yet
        val updateRPMAction = RobotAction.UpdateFlywheelRPM(2000.0, baseTime + 100L)
        state = rootReducer(state, updateRPMAction)
        
        val actions2 = executor.update(state, baseTime + 100L)
        assertTrue(actions2.isEmpty()) // Still waiting
        assertEquals("FlywheelReady(4000.0 RPM)", executor.getActiveTaskName())

        // Ramped up to speed
        val readyRPMAction = RobotAction.UpdateFlywheelRPM(3900.0, baseTime + 200L)
        state = rootReducer(state, readyRPMAction)
        assertEquals(SuperstructureMode.FLYWHEEL_READY, state.superstructure.mode)

        // Now update should complete flywheel task and trigger ShootTask immediately (which activates transfer)
        // We set inventory count to 1 first so ShootTask can transfer
        val setInventory = RobotAction.SetInventoryCount(1, baseTime + 200L)
        state = rootReducer(state, setInventory)

        val actions3 = executor.update(state, baseTime + 200L)
        assertEquals(1, actions3.size)
        assertTrue(actions3[0] is RobotAction.SetTransferActive)
        assertTrue((actions3[0] as RobotAction.SetTransferActive).active)

        state = rootReducer(state, actions3[0])
        assertEquals("Shoot", executor.getActiveTaskName())
        assertEquals(SuperstructureMode.SHOOTING, state.superstructure.mode)

        // Simulate shooting (inventory drops to 0)
        val shotAction = RobotAction.SetInventoryCount(0, baseTime + 300L)
        state = rootReducer(state, shotAction)

        // Should complete ShootTask and turn off transfer
        val actions4 = executor.update(state, baseTime + 300L)
        assertEquals(1, actions4.size)
        assertTrue(actions4[0] is RobotAction.SetTransferActive)
        assertFalse((actions4[0] as RobotAction.SetTransferActive).active)
    }

    @Test
    fun `test preemption and stack restoration`() {
        val executor = TaskExecutor()
        var state = RobotState()
        val baseTime = 1000L

        // Standard task: wait 1000ms
        executor.addTask(TimeWaitTask(1000L))

        // Start standard task
        executor.update(state, baseTime)
        assertEquals("TimeWait(1000 ms)", executor.getActiveTaskName())

        // 300ms has elapsed, now we preempt it with a high-priority quick dispatch
        val preemptionAction = RobotAction.SetIntakeActive(true, baseTime + 300L)
        val preemptActions = executor.preempt(
            ActionDispatchTask(preemptionAction),
            state,
            baseTime + 300L
        )

        // Preempt starts preemptive task instantly
        assertEquals(1, preemptActions.size)
        assertTrue(preemptActions[0] is RobotAction.SetIntakeActive)
        assertEquals("ActionDispatch(SetIntakeActive)", executor.getActiveTaskName())
        assertEquals(2, executor.size()) // 1 active, 1 preempted on stack

        state = rootReducer(state, preemptActions[0])
        assertTrue(state.superstructure.intakeActive)

        // Updating executes the ActionDispatch, completes it, and resumes TimeWaitTask instantly
        val resumeActions = executor.update(state, baseTime + 300L)
        assertTrue(resumeActions.isEmpty())
        assertEquals("TimeWait(1000 ms)", executor.getActiveTaskName())

        // Ensure remaining wait time math is intact (needs 700ms more from baseTime + 300L -> finishes at baseTime + 1000L)
        // Check halfway at 800ms total (500ms since preemption)
        val halfActions = executor.update(state, baseTime + 800L)
        assertTrue(halfActions.isEmpty())
        assertEquals("TimeWait(1000 ms)", executor.getActiveTaskName())

        // Check completion at 1000ms total
        val finalActions = executor.update(state, baseTime + 1000L)
        assertNull(executor.getActiveTaskName())
        assertEquals(0, executor.size())
    }

    @Test
    fun `test suspension blocks execution`() {
        val executor = TaskExecutor()
        val state = RobotState()
        val baseTime = 1000L

        executor.addTask(TimeWaitTask(1000L))
        executor.update(state, baseTime)

        executor.suspend()

        // 2000ms elapsed, but suspended, so should not complete
        val actions = executor.update(state, baseTime + 2000L)
        assertTrue(actions.isEmpty())
        assertEquals("TimeWait(1000 ms)", executor.getActiveTaskName())

        executor.resume()

        // Now that it is resumed, update should complete it
        val resumeActions = executor.update(state, baseTime + 2000L)
        assertNull(executor.getActiveTaskName())
    }
}
