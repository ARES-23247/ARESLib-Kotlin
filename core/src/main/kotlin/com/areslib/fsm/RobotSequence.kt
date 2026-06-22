package com.areslib.fsm

import com.areslib.action.RobotAction
import com.areslib.pathing.Path
import com.areslib.pathing.HolonomicPathFollower
import com.areslib.math.FieldSymmetry

/**
 * A fluid builder API to construct sequential and parallel autonomous sequences of [Task] objects.
 * Hides task class instantiation boilerplate behind clean, chainable method calls.
 */
class RobotSequence {
    private val tasks = mutableListOf<Task>()

    /**
     * Commands the robot to follow a trajectory path.
     */
    fun followPath(
        path: Path,
        follower: HolonomicPathFollower,
        symmetry: FieldSymmetry = FieldSymmetry.ROTATIONAL
    ): RobotSequence {
        tasks.add(FollowPathTask(follower, path, symmetry))
        return this
    }

    /**
     * Blocks execution progress until the active path tracking covers the specified distance.
     */
    fun waitDistance(meters: Double): RobotSequence {
        tasks.add(PathProgressWaitTask(meters))
        return this
    }

    /**
     * Blocks execution for a specified duration of time in milliseconds.
     */
    fun waitTime(ms: Long): RobotSequence {
        tasks.add(TimeWaitTask(ms))
        return this
    }

    /**
     * Dispatches a single Redux [RobotAction] instantly.
     */
    fun dispatch(action: RobotAction): RobotSequence {
        tasks.add(ActionDispatchTask(action))
        return this
    }

    /**
     * Appends a generic [Task] to the sequence.
     */
    fun addTask(task: Task): RobotSequence {
        tasks.add(task)
        return this
    }

    /**
     * Appends a group of tasks that will execute concurrently in parallel.
     */
    fun addParallel(vararg tasks: Task): RobotSequence {
        this.tasks.add(ParallelTaskGroup(tasks.toList()))
        return this
    }

    /**
     * Appends a group of tasks that will execute sequentially.
     */
    fun addSequential(vararg tasks: Task): RobotSequence {
        this.tasks.add(SequentialTaskGroup(tasks.toList()))
        return this
    }

    /**
     * Compiles the chained methods into a single composite [Task] (a [SequentialTaskGroup]).
     */
    fun build(): Task {
        return SequentialTaskGroup(tasks)
    }
}
