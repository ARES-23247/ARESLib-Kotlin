package com.areslib.fsm

import com.areslib.action.RobotAction
import com.areslib.state.RobotState

/**
 * Interface representing a discrete superstructure or autonomous execution task.
 */
interface Task {
    val name: String
    val priority: Int get() = 0

    /**
     * Called once when the task becomes active.
     * Returns any initial actions that should be dispatched to the Redux store.
     */
    fun initialize(state: RobotState): List<RobotAction> = emptyList()

    /**
     * Checked periodically. Returns true when the wait conditions/guards are satisfied.
     */
    fun isCompleted(state: RobotState, elapsedMs: Long): Boolean

    /**
     * Called periodically while the task is running.
     * Returns actions to dispatch during execution (e.g. feedback adjustments).
     */
    fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> = emptyList()

    /**
     * Called once when isCompleted returns true.
     * Returns any terminal cleanup actions to dispatch.
     */
    fun end(state: RobotState, interrupted: Boolean): List<RobotAction> = emptyList()
}

/**
 * Task to activate the flywheel and wait until it spins up to the target speed.
 */
class FlywheelReadyTask(
    private val targetRPM: Double,
    private val timestampMs: Long,
    private val timeoutMs: Long = 4000L
) : Task {
    override val name = "FlywheelReady($targetRPM RPM)"

    override fun initialize(state: RobotState): List<RobotAction> {
        return listOf(
            RobotAction.SetFlywheelActive(active = true, timestampMs = timestampMs)
        )
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        // Reduced state updates the superstructure.mode to ready or check RPM directly
        return state.superstructure.flywheelRPM >= targetRPM * 0.95 || elapsedMs >= timeoutMs
    }
}

/**
 * Task to activate intake and wait until inventoryCount reaches target.
 */
class IntakeUntilCountTask(
    private val targetCount: Int,
    private val timestampMs: Long,
    private val timeoutMs: Long = 5000L
) : Task {
    override val name = "IntakeUntilCount($targetCount)"

    override fun initialize(state: RobotState): List<RobotAction> {
        return listOf(
            RobotAction.SetIntakeActive(active = true, timestampMs = timestampMs)
        )
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        return state.superstructure.inventoryCount >= targetCount || elapsedMs >= timeoutMs
    }

    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {
        return listOf(
            RobotAction.SetIntakeActive(active = false, timestampMs = timestampMs)
        )
    }
}

/**
 * Task to activate transfer and shoot. Blocks until inventoryCount decreases.
 */
class ShootTask(
    private val timestampMs: Long
) : Task {
    override val name = "Shoot"
    private var initialCount: Int = -1

    override fun initialize(state: RobotState): List<RobotAction> {
        initialCount = state.superstructure.inventoryCount
        return listOf(
            RobotAction.SetTransferActive(active = true, timestampMs = timestampMs)
        )
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        // Complete if count decreases or becomes zero, or we time out after 3 seconds safety window
        val currentCount = state.superstructure.inventoryCount
        return currentCount < initialCount || currentCount == 0 || elapsedMs > 3000L
    }

    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {
        return listOf(
            RobotAction.SetTransferActive(active = false, timestampMs = timestampMs)
        )
    }
}

/**
 * Task to wait for a specific duration of time.
 */
class TimeWaitTask(
    private val durationMs: Long
) : Task {
    override val name = "TimeWait($durationMs ms)"

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        return elapsedMs >= durationMs
    }
}

/**
 * Task to block execution until path progress reaches a certain distance.
 */
class PathProgressWaitTask(
    private val targetDistanceMeters: Double,
    private val timeoutMs: Long = 10000L
) : Task {
    override val name = "PathProgressWait($targetDistanceMeters m)"

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        return state.pathState.currentDistanceMeters >= targetDistanceMeters || elapsedMs >= timeoutMs
    }
}

/**
 * Task to instantly dispatch a single Redux action and finish.
 */
class ActionDispatchTask(
    private val action: RobotAction
) : Task {
    override val name = "ActionDispatch(${action::class.simpleName})"

    private var dispatched = false

    override fun initialize(state: RobotState): List<RobotAction> {
        dispatched = true
        return listOf(action)
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        return dispatched
    }
}

/**
 * Task group that runs a list of tasks sequentially, one after another.
 */
class SequentialTaskGroup(private val tasks: List<Task>) : Task {
    override val name = "Sequential(${tasks.joinToString { it.name }})"
    private var currentIndex = 0
    private var currentTaskStartTimeMs = 0L
    private val pendingActions = mutableListOf<RobotAction>()

    override fun initialize(state: RobotState): List<RobotAction> {
        currentIndex = 0
        currentTaskStartTimeMs = 0L
        pendingActions.clear()
        if (tasks.isEmpty()) return emptyList()
        return tasks[0].initialize(state)
    }

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

    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {
        val actions = mutableListOf<RobotAction>()
        if (pendingActions.isNotEmpty()) {
            actions.addAll(pendingActions)
            pendingActions.clear()
        }
        if (currentIndex < tasks.size) {
            val currentTask = tasks[currentIndex]
            val currentTaskElapsed = elapsedMs - currentTaskStartTimeMs
            actions.addAll(currentTask.execute(state, currentTaskElapsed))
        }
        return actions
    }

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

    override fun initialize(state: RobotState): List<RobotAction> {
        completedTasks.clear()
        pendingActions.clear()
        return tasks.flatMap { it.initialize(state) }
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        for (task in tasks) {
            if (!completedTasks.contains(task)) {
                if (task.isCompleted(state, elapsedMs)) {
                    completedTasks.add(task)
                    pendingActions.addAll(task.end(state, interrupted = false))
                }
            }
        }
        return completedTasks.size == tasks.size
    }

    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {
        val actions = mutableListOf<RobotAction>()
        if (pendingActions.isNotEmpty()) {
            actions.addAll(pendingActions)
            pendingActions.clear()
        }
        for (task in tasks) {
            if (!completedTasks.contains(task)) {
                actions.addAll(task.execute(state, elapsedMs))
            }
        }
        return actions
    }

    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {
        val actions = mutableListOf<RobotAction>()
        if (pendingActions.isNotEmpty()) {
            actions.addAll(pendingActions)
            pendingActions.clear()
        }
        if (interrupted) {
            for (task in tasks) {
                if (!completedTasks.contains(task)) {
                    actions.addAll(task.end(state, interrupted = true))
                }
            }
        }
        return actions
    }
}

/**
 * Task that commands the robot to follow a specific trajectory path.
 */
class FollowPathTask @kotlin.jvm.JvmOverloads constructor(
    private val follower: com.areslib.pathing.HolonomicPathFollower,
    private val path: com.areslib.pathing.Path,
    private val symmetry: com.areslib.math.FieldSymmetry = com.areslib.math.FieldSymmetry.ROTATIONAL,
    private val fieldLength: Double = com.areslib.math.CoordinateTransformers.FTC_FIELD_SIZE,
    private val fieldWidth: Double = com.areslib.math.CoordinateTransformers.FTC_FIELD_SIZE
) : Task {
    override val name = "FollowPath(${path.points.size} points)"
    private var lastTimeMs = 0L
    private lateinit var activePath: com.areslib.pathing.Path

    override fun initialize(state: RobotState): List<RobotAction> {
        lastTimeMs = com.areslib.util.RobotClock.currentTimeMillis()
        val alliance = state.drive.alliance
        activePath = com.areslib.math.AllianceMirroring.mirror(path, alliance, symmetry, fieldLength, fieldWidth)
        return listOf(
            RobotAction.SwitchPath(activePath, isDetour = false, timestampMs = lastTimeMs)
        )
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        if (activePath.points.isEmpty()) return true
        val targetDistance = activePath.points.last().distanceMeters
        return state.pathState.currentDistanceMeters >= targetDistance || elapsedMs >= 15000L
    }

    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {
        val currentTimestamp = com.areslib.util.RobotClock.currentTimeMillis()
        val dt = if (lastTimeMs == 0L || currentTimestamp <= lastTimeMs) 0.02 else (currentTimestamp - lastTimeMs) / 1000.0
        lastTimeMs = currentTimestamp

        val currentDistance = state.pathState.currentDistanceMeters
        val targetPoint = activePath.sampleAtDistance(currentDistance)
        follower.update(targetPoint, dt)

        val nextDistance = currentDistance + targetPoint.velocityMps * dt
        return listOf(
            RobotAction.UpdatePathProgress(nextDistance, currentTimestamp)
        )
    }

    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {
        follower.stop()
        return emptyList()
    }
}

