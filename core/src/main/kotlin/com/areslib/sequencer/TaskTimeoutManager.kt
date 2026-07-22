package com.areslib.sequencer

import com.areslib.util.RobotClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Object implementation for Task Timeout Manager.
 *
 * Asynchronous superstructure task sequence execution unit.
 */
object TaskTimeoutManager {
    private val timeouts = ConcurrentHashMap<Task, Long>()
    private val startTimes = ConcurrentHashMap<Task, Long>()

    /**
     * setTimeout declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setTimeout(task: Task, ms: Long) {
        timeouts[task] = ms
    }

    /**
     * start declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun start(task: Task) {
        startTimes[task] = RobotClock.currentTimeMillis()
    }
    
    /**
     * reset declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun reset(task: Task) {
        timeouts.remove(task)
        startTimes.remove(task)
    }

    /**
     * isTimedOut declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun isTimedOut(task: Task, elapsedMs: Long): Boolean {
        val timeoutMs = timeouts[task] ?: return false
        return elapsedMs > timeoutMs
    }
}
