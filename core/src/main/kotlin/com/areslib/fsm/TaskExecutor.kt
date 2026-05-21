package com.areslib.fsm

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import java.util.ArrayDeque

/**
 * Thread-safe state-machine execution sequencer coordinating queued, conditional tasks.
 * Supports task preemption to temporarily pause and resume running tasks dynamically.
 */
class TaskExecutor {
    private val queue = ArrayDeque<Task>()
    private var activeTask: Task? = null
    private var activeTaskStartTimeMs: Long = 0L
    private var isSuspended = false
    
    // Stack of (Task, elapsedMsBeforePreemption) to enable nested preemption & resumption
    private val preemptedStack = ArrayDeque<Pair<Task, Long>>()

    /**
     * Appends a task to the standard queue.
     */
    @Synchronized
    fun addTask(task: Task) {
        queue.offer(task)
    }

    /**
     * Suspends execution of all tasks.
     */
    @Synchronized
    fun suspend() {
        isSuspended = true
    }

    /**
     * Resumes execution of tasks.
     */
    @Synchronized
    fun resume() {
        isSuspended = false
    }

    /**
     * Preempts the active task queue immediately with a high-priority task.
     * The currently active task is paused and pushed to the preemption stack, to be resumed later.
     */
    @Synchronized
    fun preempt(task: Task, state: RobotState, currentTimestampMs: Long): List<RobotAction> {
        val actions = mutableListOf<RobotAction>()
        val currentActive = activeTask
        if (currentActive != null) {
            val elapsed = currentTimestampMs - activeTaskStartTimeMs
            preemptedStack.push(Pair(currentActive, elapsed))
            actions.addAll(currentActive.end(state, interrupted = true))
        }
        
        activeTask = task
        activeTaskStartTimeMs = currentTimestampMs
        actions.addAll(task.initialize(state))
        return actions
    }

    /**
     * Evaluates the active task queue based on the latest RobotState.
     * Returns a list of actions to dispatch to the Redux store.
     */
    @Synchronized
    fun update(state: RobotState, currentTimestampMs: Long): List<RobotAction> {
        if (isSuspended) return emptyList()
        val actions = mutableListOf<RobotAction>()

        var task = activeTask
        var loopCount = 0
        val maxLoopCount = 100 // Prevent infinite loop stack overflows
        
        while (loopCount < maxLoopCount) {
            loopCount++
            if (task == null) {
                if (preemptedStack.isNotEmpty()) {
                    // Resume a previously preempted task
                    val (resumedTask, priorElapsed) = preemptedStack.pop()
                    activeTask = resumedTask
                    activeTaskStartTimeMs = currentTimestampMs - priorElapsed
                    task = resumedTask
                } else if (queue.isNotEmpty()) {
                    // Dequeue the next task
                    val nextTask = queue.poll()
                    activeTask = nextTask
                    activeTaskStartTimeMs = currentTimestampMs
                    actions.addAll(nextTask.initialize(state))
                    task = nextTask
                } else {
                    break
                }
            }

            if (task != null) {
                val elapsed = currentTimestampMs - activeTaskStartTimeMs
                if (task.isCompleted(state, elapsed)) {
                    // Finalize active task
                    actions.addAll(task.end(state, interrupted = false))
                    activeTask = null
                    task = null // Continue loop to dequeue/resume instantly
                } else {
                    actions.addAll(task.execute(state, elapsed))
                    break // Stop frame update as active task is currently running
                }
            }
        }

        if (loopCount >= maxLoopCount) {
            System.err.println("TaskExecutor: Loop transition threshold reached ($maxLoopCount). Aborting update to prevent lockup.")
        }

        return actions
    }

    /**
     * Clear all tasks in queue and stack.
     */
    @Synchronized
    fun clear() {
        queue.clear()
        preemptedStack.clear()
        activeTask = null
    }

    /**
     * Returns the name of the currently active task, if any.
     */
    @Synchronized
    fun getActiveTaskName(): String? = activeTask?.name

    /**
     * Gets total tasks currently loaded/executing (queue + active + preempted).
     */
    @Synchronized
    fun size(): Int {
        return queue.size + (if (activeTask != null) 1 else 0) + preemptedStack.size
    }
}
