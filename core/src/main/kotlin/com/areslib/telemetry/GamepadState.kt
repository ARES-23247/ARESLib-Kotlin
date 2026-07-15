package com.areslib.telemetry

/**
 * Platform-agnostic snapshot of a single gamepad's state.
 * Lives in :core so the logging/replay pipeline can serialize it
 * without depending on any FTC or FRC SDK types.
 */
class GamepadState(
    var leftStickX: Float = 0f,
    var leftStickY: Float = 0f,
    var rightStickX: Float = 0f,
    var rightStickY: Float = 0f,
    var leftTrigger: Float = 0f,
    var rightTrigger: Float = 0f,
    var a: Boolean = false,
    var b: Boolean = false,
    var x: Boolean = false,
    var y: Boolean = false,
    var dpadUp: Boolean = false,
    var dpadDown: Boolean = false,
    var dpadLeft: Boolean = false,
    var dpadRight: Boolean = false,
    var leftBumper: Boolean = false,
    var rightBumper: Boolean = false,
    var leftStickButton: Boolean = false,
    var rightStickButton: Boolean = false,
    var start: Boolean = false,
    var back: Boolean = false
) {
    fun copyFrom(other: GamepadState) {
        this.leftStickX = other.leftStickX
        this.leftStickY = other.leftStickY
        this.rightStickX = other.rightStickX
        this.rightStickY = other.rightStickY
        this.leftTrigger = other.leftTrigger
        this.rightTrigger = other.rightTrigger
        this.a = other.a
        this.b = other.b
        this.x = other.x
        this.y = other.y
        this.dpadUp = other.dpadUp
        this.dpadDown = other.dpadDown
        this.dpadLeft = other.dpadLeft
        this.dpadRight = other.dpadRight
        this.leftBumper = other.leftBumper
        this.rightBumper = other.rightBumper
        this.leftStickButton = other.leftStickButton
        this.rightStickButton = other.rightStickButton
        this.start = other.start
        this.back = other.back
    }
}
