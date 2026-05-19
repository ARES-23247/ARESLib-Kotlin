package com.qualcomm.hardware.rev

class RevHubOrientationOnRobot(
    val logoFacingDirection: LogoFacingDirection,
    val usbFacingDirection: UsbFacingDirection
) {
    enum class LogoFacingDirection {
        UP, DOWN, FORWARD, BACKWARD, LEFT, RIGHT
    }
    enum class UsbFacingDirection {
        UP, DOWN, FORWARD, BACKWARD, LEFT, RIGHT
    }
}
