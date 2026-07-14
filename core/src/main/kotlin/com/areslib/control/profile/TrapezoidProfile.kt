package com.areslib.control.profile

/**
 * A zero-allocation trapezoidal motion profile generator.
 * This class computes the trajectory to transition from a starting state to a goal state,
 * enforcing maximum velocity and acceleration limits.
 */
class TrapezoidProfile {
    private val currentLocal = State()
    private val goalLocal = State()

    /**
     * Profile constraints defining the physical limits of the mechanism.
     * @property maxVelocity Maximum allowed velocity (units/sec).
     * @property maxAcceleration Maximum allowed acceleration (units/sec^2).
     */
    data class Constraints(
        var maxVelocity: Double = 0.0,
        var maxAcceleration: Double = 0.0
    )

    /**
     * Trajectory state at a specific point in time.
     * @property position The position of the mechanism (e.g. in meters or degrees).
     * @property velocity The velocity of the mechanism (e.g. in units/sec).
     */
    data class State(
        var position: Double = 0.0,
        var velocity: Double = 0.0
    ) {
        fun setTo(other: State) {
            this.position = other.position
            this.velocity = other.velocity
        }
    }

    /**
     * Computes the state of the profile after dtSeconds, writing the result directly into [outState]
     * to prevent heap allocations.
     *
     * @param dtSeconds Timestep duration in seconds.
     * @param current The starting/current state of the mechanism.
     * @param goal The final target state of the mechanism.
     * @param constraints The velocity and acceleration constraints.
     * @param outState The pre-allocated output state where the result is stored.
     */
    fun calculate(
        dtSeconds: Double,
        current: State,
        goal: State,
        constraints: Constraints,
        outState: State
    ) {
        if (dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds <= 0.0) {
            outState.setTo(current)
            return
        }

        // Handle case where constraints or inputs are invalid
        if (!constraints.maxVelocity.isFinite() || constraints.maxVelocity <= 0.0 ||
            !constraints.maxAcceleration.isFinite() || constraints.maxAcceleration <= 0.0) {
            outState.setTo(goal)
            return
        }

        // Determine direction of motion
        val cutoff = current.position <= goal.position
        val direction = if (cutoff) 1.0 else -1.0

        currentLocal.position = current.position * direction
        currentLocal.velocity = current.velocity * direction
        goalLocal.position = goal.position * direction
        goalLocal.velocity = goal.velocity * direction

        // Clamp goal velocity to constraints
        goalLocal.velocity = goalLocal.velocity.coerceIn(-constraints.maxVelocity, constraints.maxVelocity)

        val maxVel = constraints.maxVelocity
        val maxAcc = constraints.maxAcceleration

        // Deceleration distance to go from current local velocity to goal local velocity
        val decelDist = if (currentLocal.velocity > goalLocal.velocity) {
            (currentLocal.velocity * currentLocal.velocity - goalLocal.velocity * goalLocal.velocity) / (2.0 * maxAcc)
        } else {
            0.0
        }

        val totalDist = goalLocal.position - currentLocal.position

        var nextVel: Double
        var nextPos: Double

        if (totalDist > decelDist) {
            // We can accelerate towards maxVel
            val stepVel = currentLocal.velocity + maxAcc * dtSeconds
            nextVel = kotlin.math.min(stepVel, maxVel)
            val avgVel = (currentLocal.velocity + nextVel) / 2.0
            nextPos = currentLocal.position + avgVel * dtSeconds
            
            // If we overshoot the decel bound, clamp/adjust
            val newTotalDist = goalLocal.position - nextPos
            val newDecelDist = (nextVel * nextVel - goalLocal.velocity * goalLocal.velocity) / (2.0 * maxAcc)
            if (newTotalDist < newDecelDist) {
                val overshootRatio = (newDecelDist - newTotalDist) / (newDecelDist + 1e-9)
                nextVel = nextVel - overshootRatio * maxAcc * dtSeconds
                nextVel = kotlin.math.max(nextVel, goalLocal.velocity)
                nextPos = currentLocal.position + ((currentLocal.velocity + nextVel) / 2.0) * dtSeconds
            }
        } else {
            // Decelerate towards goal velocity
            val stepVel = currentLocal.velocity - maxAcc * dtSeconds
            nextVel = kotlin.math.max(stepVel, goalLocal.velocity)
            val avgVel = (currentLocal.velocity + nextVel) / 2.0
            nextPos = currentLocal.position + avgVel * dtSeconds
        }

        // Clamp to goal if we passed it
        if (nextPos >= goalLocal.position) {
            outState.position = goal.position
            outState.velocity = goal.velocity
        } else {
            outState.position = nextPos * direction
            outState.velocity = nextVel * direction
        }
    }
}
