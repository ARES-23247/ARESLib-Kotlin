package com.areslib.control.profile

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Zero-allocation 1D kinematic Trapezoidal Motion Profile generator.
 *
 * Computes deterministic position and velocity setpoints to transition a physical mechanism from an initial
 * state $(x_0, v_0)$ to a target goal state $(x_{goal}, v_{goal})$ while respecting physical maximum velocity ($v_{max}$)
 * and maximum acceleration ($a_{max}$) constraints.
 *
 * ### Kinematic Equations:
 * Accelerated motion phase ($t \le t_{accel}$):
 * $$v(t) = v_0 + a_{max} \cdot t$$
 * $$x(t) = x_0 + v_0 \cdot t + \frac{1}{2} a_{max} \cdot t^2$$
 * Constant velocity cruise phase ($t_{accel} < t \le t_{decel}$):
 * $$v(t) = v_{cruise}$$
 * $$x(t) = x_{accel} + v_{cruise} \cdot (t - t_{accel})$$
 * Deceleration phase ($t > t_{decel}$):
 * $$v(t) = v_{cruise} - a_{max} \cdot (t - t_{decel})$$
 *
 * ### Physical Units & Properties:
 * - Position: Meters ($m$) or Radians ($rad$)
 * - Velocity: Meters per second ($m/s$) or Radians per second ($rad/s$)
 * - Acceleration: Meters per second squared ($m/s^2$) or Radians per second squared ($rad/s^2$)
 * - Timestep ($\Delta t$): Seconds ($s$)
 * - Zero-GC Footprint: Writes directly into a pre-allocated output [State] instance.
 */
class TrapezoidProfile {
    private val currentLocal = State()
    private val goalLocal = State()

    /**
     * Profile constraints defining maximum kinematic bounds for the mechanism.
     *
     * @property maxVelocity Maximum allowable cruise velocity $v_{max}$ (units/s).
     * @property maxAcceleration Maximum allowable acceleration $a_{max}$ (units/$s^2$).
     */
    data class Constraints(
        var maxVelocity: Double = 0.0,
        var maxAcceleration: Double = 0.0
    )

    /**
     * Trajectory state representation at a discrete time instant.
     *
     * @property position Mechanism position $x(t)$ (meters or radians).
     * @property velocity Mechanism velocity $v(t)$ (units/s).
     */
    data class State(
        var position: Double = 0.0,
        var velocity: Double = 0.0
    ) {
        /**
         * Copies values from another state into this instance without heap allocations.
         *
         * @param other Source state to copy.
         */
        fun setTo(other: State) {
            this.position = other.position
            this.velocity = other.velocity
        }
    }

    /**
     * Computes the interpolated profile state after $\Delta t$ seconds, writing the result directly into [outState].
     *
     * @param dtSeconds Timestep duration in seconds ($\Delta t > 0$).
     * @param current Current mechanism state $(x_0, v_0)$.
     * @param goal Target mechanism state $(x_{goal}, v_{goal})$.
     * @param constraints Physical velocity and acceleration constraints.
     * @param outState Pre-allocated output state receiving the calculated trajectory point.
     */
    fun calculate(
        dtSeconds: Double,
        current: State,
        goal: State,
        constraints: Constraints,
        outState: State
    ) {
        currentLocal.setTo(current)
        goalLocal.setTo(goal)

        val direction = if (goalLocal.position < currentLocal.position) -1.0 else 1.0
        val dist = abs(goalLocal.position - currentLocal.position)

        if (dist < 1e-6 || constraints.maxAcceleration <= 0.0 || constraints.maxVelocity <= 0.0) {
            outState.setTo(goalLocal)
            return
        }

        val maxV = constraints.maxVelocity
        val maxA = constraints.maxAcceleration

        // Calculate time needed to reach goal
        val accelTime = maxV / maxA
        val accelDist = 0.5 * maxA * accelTime * accelTime

        val cruiseDist: Double
        val actualMaxV: Double
        val actualAccelTime: Double

        if (2 * accelDist > dist) {
            // Triangular profile
            actualAccelTime = sqrt(dist / maxA)
            actualMaxV = maxA * actualAccelTime
            cruiseDist = 0.0
        } else {
            // Trapezoidal profile
            actualAccelTime = accelTime
            actualMaxV = maxV
            cruiseDist = dist - 2 * accelDist
        }

        val cruiseTime = if (actualMaxV > 0) cruiseDist / actualMaxV else 0.0
        val decelTime = actualAccelTime
        val totalTime = actualAccelTime + cruiseTime + decelTime

        if (dtSeconds >= totalTime) {
            outState.setTo(goalLocal)
            return
        }

        var newPos: Double
        var newVel: Double

        if (dtSeconds <= actualAccelTime) {
            // Acceleration phase
            newVel = currentLocal.velocity + direction * maxA * dtSeconds
            if (abs(newVel) > actualMaxV) newVel = direction * actualMaxV
            newPos = currentLocal.position + currentLocal.velocity * dtSeconds + 0.5 * direction * maxA * dtSeconds * dtSeconds
        } else if (dtSeconds <= actualAccelTime + cruiseTime) {
            // Constant velocity phase
            val cruiseDt = dtSeconds - actualAccelTime
            val startCruisePos = currentLocal.position + 0.5 * direction * maxA * actualAccelTime * actualAccelTime
            newVel = direction * actualMaxV
            newPos = startCruisePos + newVel * cruiseDt
        } else {
            // Deceleration phase
            val decelDt = dtSeconds - actualAccelTime - cruiseTime
            val startDecelPos = currentLocal.position + (0.5 * direction * maxA * actualAccelTime * actualAccelTime) + (direction * actualMaxV * cruiseTime)
            val startDecelVel = direction * actualMaxV
            newVel = startDecelVel - direction * maxA * decelDt
            newPos = startDecelPos + startDecelVel * decelDt - 0.5 * direction * maxA * decelDt * decelDt
        }

        // Final sanity check for direction overshooting
        if (direction > 0 && newPos > goalLocal.position) {
            newPos = goalLocal.position
            newVel = goalLocal.velocity
        } else if (direction < 0 && newPos < goalLocal.position) {
            newPos = goalLocal.position
            newVel = goalLocal.velocity
        }

        outState.position = newPos
        outState.velocity = newVel
    }
}
