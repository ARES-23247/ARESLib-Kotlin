package com.areslib.subsystem

import com.areslib.fsm.*
import com.areslib.state.SuperstructureMode

/**
 * A highly simplified, student-facing modular facade for the superstructure subsystem.
 * Exposes subsystem states and pre-packaged task factories (commands) for autonomous routines.
 */
class SuperstructureFacade(private val store: Store) {

    /** The current state mode of the superstructure finite state machine. */
    val mode: SuperstructureMode
        get() = store.state.superstructure.mode

    /** True if the intake motor is actively running. */
    val intakeActive: Boolean
        get() = store.state.superstructure.intakeActive

    /** True if the shooter flywheel motor is actively running. */
    val flywheelActive: Boolean
        get() = store.state.superstructure.flywheelActive

    /** True if the transfer mechanism motor is feeding game pieces. */
    val transferActive: Boolean
        get() = store.state.superstructure.transferActive

    /** The current RPM speed of the shooter flywheel. */
    val flywheelRPM: Double
        get() = store.state.superstructure.flywheelRPM

    /** The current number of game pieces inside the robot's inventory indexer. */
    val inventoryCount: Int
        get() = store.state.superstructure.inventoryCount

    /**
     * Inline Command Factory: Activates the flywheel and suspends progress until the target RPM speed is met.
     */
    fun flywheelReadyCommand(targetRPM: Double, timestampMs: Long): Task {
        return FlywheelReadyTask(targetRPM, timestampMs)
    }

    /**
     * Inline Command Factory: Activates the intake and suspends progress until a target count of game pieces is secured.
     */
    fun intakeUntilCountCommand(targetCount: Int, timestampMs: Long): Task {
        return IntakeUntilCountTask(targetCount, timestampMs)
    }

    /**
     * Inline Command Factory: Feeds a game piece to the shooter flywheel and blocks until the piece is expelled.
     */
    fun shootCommand(timestampMs: Long): Task {
        return ShootTask(timestampMs)
    }
}
