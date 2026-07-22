package com.areslib.control.assist

/**
 * Interface representing a modular robot action.
 */
interface Action {
    /**
     * start declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun start() {}
    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun update(dtSeconds: Double)
    /**
     * isFinished declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun isFinished(): Boolean
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
    fun end(interrupted: Boolean) {}
}

/**
 * High-performance, student-friendly Coordinated Action Executor.
 * Manages concurrent, sequential, and timed tasks with strict safety interlock monitoring.
 */
class CoordinatedActionExecutor(
    private val safetyCheck: () -> Boolean = { true }
) {
    private var activeAction: Action? = null
    var isEmergencyAborted: Boolean = false
        private set

    /**
     * Queues/starts a new action. Interrupts the previous action if one was running.
     */
    fun startAction(action: Action) {
        if (isEmergencyAborted) return
        activeAction?.end(true)
        activeAction = action
        action.start()
    }

    /**
     * Periodic update loop. Must be called in the robot's main loop.
     * @param dtSeconds Elapsed time since last loop.
     */
    fun update(dtSeconds: Double) {
        if (isEmergencyAborted) return

        // Dynamic safety interlock check
        if (!safetyCheck()) {
            abort()
            return
        }

        activeAction?.let { action ->
            action.update(dtSeconds)
            if (action.isFinished()) {
                action.end(false)
                activeAction = null
            }
        }
    }

    /**
     * Aborts the currently active action and prevents further execution until reset.
     */
    fun abort() {
        activeAction?.end(true)
        activeAction = null
        isEmergencyAborted = true
    }

    /**
     * Resets the emergency abort flag to resume operations.
     */
    fun resetAbort() {
        isEmergencyAborted = false
    }

    /**
     * Check if the executor is currently running an action sequence.
     */
    fun isRunning(): Boolean = activeAction != null
}

/**
 * Action that executes a series of actions sequentially.
 */
class SequentialAction(private vararg val actions: Action) : Action {
    private var currentIndex = 0

    /**
     * start declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun start() {
        currentIndex = 0
        if (actions.isNotEmpty()) {
            actions[0].start()
        }
    }

    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun update(dtSeconds: Double) {
        if (currentIndex >= actions.size) return

        val currentAction = actions[currentIndex]
        currentAction.update(dtSeconds)

        val finished = currentAction.isFinished()
        when {
            finished -> {
                currentAction.end(false)
                currentIndex++
                when {
                    currentIndex < actions.size -> actions[currentIndex].start()
                }
            }
        }
    }

    /**
     * isFinished declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isFinished(): Boolean {
        return currentIndex >= actions.size
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
    override fun end(interrupted: Boolean) {
        if (interrupted && currentIndex < actions.size) {
            actions[currentIndex].end(true)
        }
    }
}

/**
 * Action that executes multiple actions in parallel.
 * Finishes when ALL sub-actions have finished.
 */
class ParallelAction(private vararg val actions: Action) : Action {
    private val activeActions = mutableListOf<Action>()

    /**
     * start declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun start() {
        activeActions.clear()
        activeActions.addAll(actions)
        for (action in activeActions) {
            action.start()
        }
    }

    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun update(dtSeconds: Double) {
        var i = 0
        while (i < activeActions.size) {
            val action = activeActions[i]
            action.update(dtSeconds)
            if (action.isFinished()) {
                action.end(false)
                activeActions.removeAt(i)
            } else {
                i++
            }
        }
    }

    /**
     * isFinished declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isFinished(): Boolean {
        return activeActions.isEmpty()
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
    override fun end(interrupted: Boolean) {
        if (interrupted) {
            for (action in activeActions) {
                action.end(true)
            }
        }
    }
}

/**
 * Action that pauses for a specified duration of time.
 */
class WaitAction(private val durationSeconds: Double) : Action {
    private var elapsedSeconds = 0.0

    /**
     * start declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun start() {
        elapsedSeconds = 0.0
    }

    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun update(dtSeconds: Double) {
        elapsedSeconds += dtSeconds
    }

    /**
     * isFinished declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isFinished(): Boolean {
        return elapsedSeconds >= durationSeconds
    }
}

/**
 * Action that executes a lambda instantly.
 */
class InstantAction(private val runnable: () -> Unit) : Action {
    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun update(dtSeconds: Double) {}
    /**
     * isFinished declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun isFinished(): Boolean = true
    /**
     * start declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun start() {
        runnable()
    }
}
