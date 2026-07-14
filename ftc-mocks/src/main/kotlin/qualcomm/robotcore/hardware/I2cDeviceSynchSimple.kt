@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.hardware

interface I2cDeviceSynchSimple : HardwareDevice {
    fun read8(ireg: Int): ByteArray
    fun write8(ireg: Int, data: Int)
}
