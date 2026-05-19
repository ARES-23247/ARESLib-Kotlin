package com.areslib.telemetry

/**
 * Platform-agnostic snapshot of a single gamepad's state.
 * Lives in :core so the logging/replay pipeline can serialize it
 * without depending on any FTC or FRC SDK types.
 */
data class GamepadState(
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val a: Boolean = false,
    val b: Boolean = false,
    val x: Boolean = false,
    val y: Boolean = false,
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false,
    val leftBumper: Boolean = false,
    val rightBumper: Boolean = false,
    val leftStickButton: Boolean = false,
    val rightStickButton: Boolean = false,
    val start: Boolean = false,
    val back: Boolean = false
)
