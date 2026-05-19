package com.areslib.ftc

import com.areslib.telemetry.GamepadState
import com.qualcomm.robotcore.hardware.Gamepad

/**
 * Converts an FTC SDK Gamepad into a platform-agnostic GamepadState
 * for the ARESLib logging pipeline.
 */
fun Gamepad.toState() = GamepadState(
    leftStickX = left_stick_x,
    leftStickY = left_stick_y,
    rightStickX = right_stick_x,
    rightStickY = right_stick_y,
    leftTrigger = left_trigger,
    rightTrigger = right_trigger,
    a = a,
    b = b,
    x = x,
    y = y,
    dpadUp = dpad_up,
    dpadDown = dpad_down,
    dpadLeft = dpad_left,
    dpadRight = dpad_right,
    leftBumper = left_bumper,
    rightBumper = right_bumper,
    leftStickButton = left_stick_button,
    rightStickButton = right_stick_button,
    start = start,
    back = back
)
