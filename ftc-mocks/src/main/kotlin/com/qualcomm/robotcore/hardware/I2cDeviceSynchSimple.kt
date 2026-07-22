@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.hardware

/**
 * Interface implementation for I2c Device Synch Simple.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
interface I2cDeviceSynchSimple : HardwareDevice {
    /**
     * read8 declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun read8(ireg: Int): Byte = 0
    /**
     * write8 declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun write8(ireg: Int, data: Int) {}
}
