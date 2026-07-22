@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.rev

/**
 * Class implementation for Rev Hub Orientation On Robot.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class RevHubOrientationOnRobot(
    val logoFacingDirection: LogoFacingDirection,
    val usbFacingDirection: UsbFacingDirection
) {
    /**
     * LogoFacingDirection declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class LogoFacingDirection {
        UP, DOWN, FORWARD, BACKWARD, LEFT, RIGHT
    }
    /**
     * UsbFacingDirection declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class UsbFacingDirection {
        UP, DOWN, FORWARD, BACKWARD, LEFT, RIGHT
    }
}
