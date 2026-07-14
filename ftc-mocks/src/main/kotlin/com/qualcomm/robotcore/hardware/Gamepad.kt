package com.qualcomm.robotcore.hardware

/**
 * Mock representation of an FTC [Gamepad].
 */
open class Gamepad {
    @JvmField var left_stick_x: Float = 0f
    @JvmField var left_stick_y: Float = 0f
    @JvmField var right_stick_x: Float = 0f
    @JvmField var right_stick_y: Float = 0f

    @JvmField var left_trigger: Float = 0f
    @JvmField var right_trigger: Float = 0f

    @JvmField var a: Boolean = false
    @JvmField var b: Boolean = false
    @JvmField var x: Boolean = false
    @JvmField var y: Boolean = false

    @JvmField var dpad_up: Boolean = false
    @JvmField var dpad_down: Boolean = false
    @JvmField var dpad_left: Boolean = false
    @JvmField var dpad_right: Boolean = false

    @JvmField var left_bumper: Boolean = false
    @JvmField var right_bumper: Boolean = false

    @JvmField var start: Boolean = false
    @JvmField var back: Boolean = false
    @JvmField var guide: Boolean = false

    @JvmField var left_stick_button: Boolean = false
    @JvmField var right_stick_button: Boolean = false
}
