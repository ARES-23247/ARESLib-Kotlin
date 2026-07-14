package com.areslib.control.feedback

import com.areslib.control.profile.TrapezoidProfile

/**
 * A zero-allocation Profiled PID Controller.
 * This class wraps a standard [PIDController] and drives a [TrapezoidProfile]
 * to enforce velocity and acceleration constraints on a closed-loop mechanism.
 */
class ProfiledPIDController(
    var p: Double,
    var i: Double,
    var d: Double,
    var constraints: TrapezoidProfile.Constraints
) {
    val pidController = PIDController(p, i, d)
    val profile = TrapezoidProfile()

    // Pre-allocated states to prevent heap allocation in control loops
    val currentState = TrapezoidProfile.State()
    val targetState = TrapezoidProfile.State()
    val nextTargetState = TrapezoidProfile.State()

    /**
     * Resets the controller and sets the current state of the mechanism.
     */
    fun reset(position: Double, velocity: Double = 0.0) {
        pidController.reset()
        currentState.position = position
        currentState.velocity = velocity
        targetState.position = position
        targetState.velocity = velocity
    }

    /**
     * Sets the overall target goal state for the controller.
     */
    fun setGoal(goalPosition: Double, goalVelocity: Double = 0.0) {
        targetState.position = goalPosition
        targetState.velocity = goalVelocity
    }

    /**
     * Sets output limits of the internal PID controller.
     */
    fun setOutputLimits(min: Double, max: Double) {
        pidController.setOutputLimits(min, max)
    }

    /**
     * Sets integrator range of the internal PID controller.
     */
    fun setIntegratorRange(min: Double, max: Double) {
        pidController.setIntegratorRange(min, max)
    }

    /**
     * Calculates the next control effort, updating the motion profile setpoint.
     *
     * @param measurement Current position measurement of the mechanism.
     * @param dtSeconds Elapsed time since the last calculation frame.
     * @return The combined feedback effort (from the PID) to drive towards the profiled reference.
     */
    fun calculate(measurement: Double, dtSeconds: Double): Double {
        // Calculate the next step of the profile reference towards the targetState
        profile.calculate(dtSeconds, currentState, targetState, constraints, nextTargetState)
        
        // Update current profile state to the computed next state
        currentState.setTo(nextTargetState)

        // Run the feedback controller relative to the profiled reference position
        pidController.setSetpoint(currentState.position)
        
        return pidController.calculate(measurement, dtSeconds)
    }
}
