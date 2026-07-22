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

    fun setTimeout(task: Task, ms: Long) {
        timeouts[task] = ms
    }

    fun start(task: Task) {
        startTimes[task] = RobotClock.currentTimeMillis()
    }
    
    fun reset(task: Task) {
        timeouts.remove(task)
        startTimes.remove(task)
    }

    fun isTimedOut(task: Task, elapsedMs: Long): Boolean {
        val timeoutMs = timeouts[task] ?: return false
        return elapsedMs > timeoutMs
    }
}
