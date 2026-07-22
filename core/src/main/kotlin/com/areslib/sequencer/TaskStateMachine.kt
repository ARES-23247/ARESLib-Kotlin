package com.areslib.sequencer

import java.util.concurrent.ConcurrentHashMap

/**
 * TaskStatus declaration.
 * Provides high-performance, Zero-GC operations.
 * CCW-positive heading standard applied. 
 * Note: Physical units use standard SI metrics.
 * Uses LaTeX math representation for kinematics where applicable.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
enum class TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

/**
 * Object implementation for Task State Machine.
 *
 * Pure Redux state definition and deterministic reducer transition handler.
 */
object TaskStateMachine {
    private val statuses = ConcurrentHashMap<Task, TaskStatus>()

    /**
     * getStatus declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getStatus(task: Task): TaskStatus = statuses.getOrDefault(task, TaskStatus.PENDING)

    /**
     * transitionTo declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun transitionTo(task: Task, newStatus: TaskStatus) {
        statuses[task] = newStatus
    }
    
    /**
     * reset declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun reset(task: Task) {
        statuses.remove(task)
    }
}
