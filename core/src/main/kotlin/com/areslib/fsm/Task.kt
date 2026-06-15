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
