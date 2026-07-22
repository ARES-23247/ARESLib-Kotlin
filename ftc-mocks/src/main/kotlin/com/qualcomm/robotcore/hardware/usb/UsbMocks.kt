@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.hardware.usb

/**
 * Class implementation for Robot Usb Device.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class RobotUsbDevice {
    /**
     * write declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun write(data: ByteArray) {}
}
