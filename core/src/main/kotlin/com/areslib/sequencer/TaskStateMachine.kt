package com.areslib.sequencer

import java.util.concurrent.ConcurrentHashMap

enum class TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

object TaskStateMachine {
    private val statuses = ConcurrentHashMap<Task, TaskStatus>()

    fun getStatus(task: Task): TaskStatus = statuses.getOrDefault(task, TaskStatus.PENDING)

    fun transitionTo(task: Task, newStatus: TaskStatus) {
        statuses[task] = newStatus
    }
    
    fun reset(task: Task) {
        statuses.remove(task)
    }
}
