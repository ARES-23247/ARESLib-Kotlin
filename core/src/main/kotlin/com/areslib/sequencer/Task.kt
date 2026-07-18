package com.areslib.sequencer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.math.geometry.Pose2d

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
    private val actionsList = mutableListOf<RobotAction>()

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

    override fun initialize(state: RobotState): List<RobotAction> {
        completedTasks.clear()
        pendingActions.clear()
        return tasks.flatMap { it.initialize(state) }
    }

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
 * Task that commands the robot to follow a specific trajectory path.
 */
class FollowPathTask @kotlin.jvm.JvmOverloads constructor(
    private val follower: com.areslib.pathing.HolonomicPathFollower,
    private val path: com.areslib.pathing.Path,
    private val symmetry: com.areslib.math.coordinate.FieldSymmetry = com.areslib.math.coordinate.FieldSymmetry.MIRRORED,
    private val fieldLength: Double = com.areslib.math.coordinate.CoordinateTransformers.FTC_FIELD_SIZE,
    private val fieldWidth: Double = com.areslib.math.coordinate.CoordinateTransformers.FTC_FIELD_SIZE,
    private val mirrorForAlliance: Boolean = true
) : Task {
    override val name = "FollowPath(${path.points.size} points)"
    private var lastTimeMs = 0L
    private lateinit var activePath: com.areslib.pathing.Path
    private val triggeredEvents = mutableSetOf<String>()
    
    private val scratchMutablePoint = com.areslib.pathing.MutablePathPoint()
    private val scratchPathPoint = com.areslib.pathing.PathPoint(Pose2d(), 0.0)
    private val actionsList = mutableListOf<RobotAction>()
    
    private val activeEventTasks = mutableListOf<Task>()
    private val taskStartTimes = mutableMapOf<Task, Long>()

    override fun initialize(state: RobotState): List<RobotAction> {
        lastTimeMs = com.areslib.util.RobotClock.currentTimeMillis()
        val alliance = if (mirrorForAlliance) state.drive.alliance else com.areslib.state.Alliance.BLUE
        activePath = com.areslib.math.coordinate.AllianceMirroring.mirror(path, alliance, symmetry, fieldLength, fieldWidth)
        triggeredEvents.clear()
        activeEventTasks.clear()
        taskStartTimes.clear()

        // Closest-point projection: start tracking from the nearest point on the path
        // to the robot's actual position, rather than always from distance 0.
        val currentPose = state.drive.poseEstimator.estimatedPose
        val startDistance = activePath.findClosestDistance(currentPose.x, currentPose.y)

        return listOf(
            RobotAction.SwitchPath(activePath, isDetour = false, startDistanceMeters = startDistance, timestampMs = lastTimeMs)
        )
    }

    override fun isCompleted(state: RobotState, elapsedMs: Long): Boolean {
        if (activePath.points.isEmpty()) return true
        val targetDistance = activePath.points.last().distanceMeters
        
        val isVirtualComplete = state.pathState.currentDistanceMeters >= targetDistance
        if (!isVirtualComplete && elapsedMs < 15000L) {
            return false
        }
        
        val currentPose = state.drive.poseEstimator.estimatedPose
        val endPose = activePath.points.last().pose
        val dx = currentPose.x - endPose.x
        val dy = currentPose.y - endPose.y
        val distToTarget = kotlin.math.sqrt(dx * dx + dy * dy)
        
        return distToTarget < 0.1 || elapsedMs >= 15000L
    }

    override fun execute(state: RobotState, elapsedMs: Long): List<RobotAction> {
        val currentTimestamp = com.areslib.util.RobotClock.currentTimeMillis()
        val dt = if (lastTimeMs == 0L || currentTimestamp <= lastTimeMs) 0.02 else (currentTimestamp - lastTimeMs) / 1000.0
        lastTimeMs = currentTimestamp

        val currentDistance = state.pathState.currentDistanceMeters
        activePath.sampleAtDistance(currentDistance, scratchMutablePoint)
        scratchMutablePoint.copyInto(scratchPathPoint)
        follower.update(scratchPathPoint, dt)

        val progressSpeed = kotlin.math.max(scratchPathPoint.velocityMps, 0.1)
        val nextDistance = currentDistance + progressSpeed * dt
        
        val currentPose = state.drive.poseEstimator.estimatedPose
        val targetPose = scratchPathPoint.pose
        val xError = targetPose.x - currentPose.x
        val yError = targetPose.y - currentPose.y
        val pathTangent = scratchPathPoint.tangentRadians
        val crossTrack = xError * kotlin.math.sin(pathTangent) - yError * kotlin.math.cos(pathTangent)
        val alongTrack = xError * kotlin.math.cos(pathTangent) + yError * kotlin.math.sin(pathTangent)
        var headingError = targetPose.heading.radians - currentPose.heading.radians
        headingError = kotlin.math.atan2(kotlin.math.sin(headingError), kotlin.math.cos(headingError))
        
        actionsList.clear()
        actionsList.add(RobotAction.UpdatePathProgress(
            distanceProgressMeters = nextDistance,
            crossTrackErrorMeters = crossTrack,
            alongTrackErrorMeters = alongTrack,
            headingErrorRadians = headingError,
            timestampMs = currentTimestamp
        ))

        // Check path events using index-based loop to prevent iterator allocation
        for (i in 0 until activePath.events.size) {
            val event = activePath.events[i]
            if (event.eventName !in triggeredEvents && event.triggerDistanceMeters <= nextDistance) {
                triggeredEvents.add(event.eventName)
                actionsList.add(RobotAction.PathEventTriggered(event.eventName, currentTimestamp))
                
                // Spawn the named command as a background task
                val cmdTask = com.areslib.pathing.NamedCommands.getCommand(event.eventName, currentTimestamp)
                if (cmdTask != null) {
                    actionsList.addAll(cmdTask.initialize(state))
                    activeEventTasks.add(cmdTask)
                    taskStartTimes[cmdTask] = currentTimestamp
                }
            }
        }
        
        // Execute active event tasks
        val it = activeEventTasks.iterator()
        while (it.hasNext()) {
            val cmdTask = it.next()
            val startTime = taskStartTimes[cmdTask] ?: currentTimestamp
            val cmdElapsed = currentTimestamp - startTime
            if (cmdTask.isCompleted(state, cmdElapsed)) {
                actionsList.addAll(cmdTask.end(state, false))
                it.remove()
                taskStartTimes.remove(cmdTask)
            } else {
                actionsList.addAll(cmdTask.execute(state, cmdElapsed))
            }
        }

        return actionsList
    }

    override fun end(state: RobotState, interrupted: Boolean): List<RobotAction> {
        follower.stop()
        val actions = mutableListOf<RobotAction>()
        for (cmdTask in activeEventTasks) {
            actions.addAll(cmdTask.end(state, interrupted = true))
        }
        activeEventTasks.clear()
        taskStartTimes.clear()
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

    override fun initialize(state: RobotState): List<RobotAction> {
        completedTasks.clear()
        pendingActions.clear()
        isCompleted = false
        return tasks.flatMap { it.initialize(state) }
    }

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

    override fun initialize(state: RobotState): List<RobotAction> {
        completedTasks.clear()
        pendingActions.clear()
        return tasks.flatMap { it.initialize(state) }
    }

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
