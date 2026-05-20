package com.areslib.subsystem

import com.areslib.state.RobotState

/**
 * A builder class implementing a fluent Kotlin DSL for configuring an [AresRobot] instance.
 */
class AresRobotBuilder {
    /**
     * The initial state configuration of the robot. Defaults to a standard [RobotState].
     */
    var initialState: RobotState = RobotState()

    /**
     * Helper to configure the initial state of the robot using a lambda block.
     */
    fun initialState(block: RobotState.() -> Unit) {
        val state = RobotState()
        state.block()
        initialState = state
    }

    /**
     * Constructs and returns the fully configured [AresRobot] instance.
     */
    fun build(): AresRobot {
        return AresRobot(initialState)
    }
}

/**
 * Creates and configures an [AresRobot] instance using a clean, fluent Kotlin DSL.
 *
 * Example usage:
 * ```kotlin
 * val robot = aresRobot {
 *     initialState {
 *         // configure state parameters here
 *     }
 * }
 * ```
 */
fun aresRobot(block: AresRobotBuilder.() -> Unit): AresRobot {
    val builder = AresRobotBuilder()
    builder.block()
    return builder.build()
}
