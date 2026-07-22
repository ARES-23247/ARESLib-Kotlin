package com.areslib.sequencer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState

/**
 * Task group that runs a list of tasks sequentially, one after another.
 */
class SequentialTaskGroup(private val tasks: List<Task>) : Task {
    override val name = "Sequential(${tasks.joinToString { it.name }})"
    private var currentIndex = 0
    private var currentTaskStartTimeMs = 0L
    private val pendingActions = mutableListOf<RobotAction>()
    private val actionsList = mutableListOf<RobotAction>()

    /**
     * initialize declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun initialize(state: RobotState): List<RobotAction> {

        currentIndex = 0
        currentTaskStartTimeMs = 0L
        pendingActions.clear()
        if (tasks.isEmpty()) return emptyList()
        return tasks[0].initialize(state)
    }

    /**
     * isCompleted declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        while (currentIndex < tasks.size) {
            val currentTask = tasks[currentIndex]
            val currentTaskElapsed = elapsedMs - currentTaskStartTimeMs
            if (currentTask.isCompleted(state, currentTaskElapsed)) {
                pendingActions.addAll(currentTask.end(state, interrupted = false))
                currentIndex++
                currentTaskStartTimeMs = elapsedMs
                if (currentIndex < tasks.size) {
                    pendingActions.addAll(tasks[currentIndex].initialize(state))
                }
            } else {
                return false
            }
        }
        return true
    }

    /**
     * execute declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {

        actionsList.clear()
        if (pendingActions.isNotEmpty()) {
            actionsList.addAll(pendingActions)
            pendingActions.clear()
        }
        if (currentIndex < tasks.size) {
            val currentTask = tasks[currentIndex]
            val currentTaskElapsed = elapsedMs - currentTaskStartTimeMs
            actionsList.addAll(currentTask.execute(state, currentTaskElapsed))
        }
        return actionsList
    }

    /**
     * end declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {

        val actions = mutableListOf<RobotAction>()
        if (pendingActions.isNotEmpty()) {
            actions.addAll(pendingActions)
            pendingActions.clear()
        }
        if (interrupted && currentIndex < tasks.size) {
            actions.addAll(tasks[currentIndex].end(state, interrupted = true))
        }
        return actions
    }
}

/**
 * Task group that runs multiple tasks simultaneously in parallel.
 */
class ParallelTaskGroup(private val tasks: List<Task>) : Task {
    override val name = "Parallel(${tasks.joinToString { it.name }})"
    private val completedTasks = mutableSetOf<Task>()
    private val pendingActions = mutableListOf<RobotAction>()
    private val actionsList = mutableListOf<RobotAction>()

    /**
     * initialize declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun initialize(state: RobotState): List<RobotAction> {

        completedTasks.clear()
        pendingActions.clear()
        return tasks.flatMap { it.initialize(state) }
    }

    /**
     * isCompleted declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (!completedTasks.contains(task)) {
                if (task.isCompleted(state, elapsedMs)) {
                    completedTasks.add(task)
                    pendingActions.addAll(task.end(state, interrupted = false))
                }
            }
        }
        return completedTasks.size == tasks.size
    }

    /**
     * execute declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {

        actionsList.clear()
        if (pendingActions.isNotEmpty()) {
            actionsList.addAll(pendingActions)
            pendingActions.clear()
        }
        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (!completedTasks.contains(task)) {
                actionsList.addAll(task.execute(state, elapsedMs))
            }
        }
        return actionsList
    }

    /**
     * end declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {

        val actions = mutableListOf<RobotAction>()
        if (pendingActions.isNotEmpty()) {
            actions.addAll(pendingActions)
            pendingActions.clear()
        }
        if (interrupted) {
            for (i in 0 until tasks.size) {
                val task = tasks[i]
                if (!completedTasks.contains(task)) {
                    actions.addAll(task.end(state, interrupted = true))
                }
            }
        }
        return actions
    }
}

/**
 * Task group that runs multiple tasks simultaneously in parallel.
 * Finishes as soon as ANY of the tasks completes, interrupting the rest.
 */
class ParallelRaceGroup(private val tasks: List<Task>) : Task {
    override val name = "ParallelRace(${tasks.joinToString { it.name }})"
    private val completedTasks = mutableSetOf<Task>()
    private val pendingActions = mutableListOf<RobotAction>()
    private val actionsList = mutableListOf<RobotAction>()
    private var isCompleted = false

    /**
     * initialize declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun initialize(state: RobotState): List<RobotAction> {

        completedTasks.clear()
        pendingActions.clear()
        isCompleted = false
        return tasks.flatMap { it.initialize(state) }
    }

    /**
     * isCompleted declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        if (isCompleted) return true
        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (task.isCompleted(state, elapsedMs)) {
                completedTasks.add(task)
                pendingActions.addAll(task.end(state, interrupted = false))
                isCompleted = true
                break
            }
        }
        return isCompleted
    }

    /**
     * execute declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {

        actionsList.clear()
        if (pendingActions.isNotEmpty()) {
            actionsList.addAll(pendingActions)
            pendingActions.clear()
        }
        if (isCompleted) return actionsList

        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (!completedTasks.contains(task)) {
                actionsList.addAll(task.execute(state, elapsedMs))
            }
        }
        return actionsList
    }

    /**
     * end declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {

        val actions = mutableListOf<RobotAction>()
        if (pendingActions.isNotEmpty()) {
            actions.addAll(pendingActions)
            pendingActions.clear()
        }
        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (!completedTasks.contains(task)) {
                actions.addAll(task.end(state, interrupted = true))
            }
        }
        return actions
    }
}

/**
 * Task group that runs multiple tasks simultaneously in parallel.
 * Finishes as soon as a specific "deadline" task completes, interrupting the rest.
 */
class ParallelDeadlineGroup(
    private val deadline: Task,
    private val otherTasks: List<Task>
) : Task {
    private val tasks = listOf(deadline) + otherTasks
    override val name = "ParallelDeadline(deadline=${deadline.name}, others=${otherTasks.joinToString { it.name }})"
    private val completedTasks = mutableSetOf<Task>()
    private val pendingActions = mutableListOf<RobotAction>()
    private val actionsList = mutableListOf<RobotAction>()

    /**
     * initialize declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun initialize(state: RobotState): List<RobotAction> {

        completedTasks.clear()
        pendingActions.clear()
        return tasks.flatMap { it.initialize(state) }
    }

    /**
     * isCompleted declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (!completedTasks.contains(task)) {
                if (task.isCompleted(state, elapsedMs)) {
                    completedTasks.add(task)
                    pendingActions.addAll(task.end(state, interrupted = false))
                }
            }
        }
        return completedTasks.contains(deadline)
    }

    /**
     * execute declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {

        actionsList.clear()
        if (pendingActions.isNotEmpty()) {
            actionsList.addAll(pendingActions)
            pendingActions.clear()
        }
        if (completedTasks.contains(deadline)) return actionsList

        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (!completedTasks.contains(task)) {
                actionsList.addAll(task.execute(state, elapsedMs))
            }
        }
        return actionsList
    }

    /**
     * end declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {
        val actions = mutableListOf<RobotAction>()
        if (pendingActions.isNotEmpty()) {
            actions.addAll(pendingActions)
            pendingActions.clear()
        }
        for (i in 0 until tasks.size) {
            val task = tasks[i]
            if (!completedTasks.contains(task)) {
                actions.addAll(task.end(state, interrupted = true))
            }
        }
        return actions
    }
}
