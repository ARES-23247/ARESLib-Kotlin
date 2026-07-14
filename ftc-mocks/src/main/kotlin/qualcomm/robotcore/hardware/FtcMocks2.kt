@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.hardware

// Append to the existing FtcMocks.kt or just define the missing ones here in a new file

interface DcMotor : DcMotorSimple {
    enum class RunMode { RUN_WITHOUT_ENCODER, RUN_USING_ENCODER, RUN_TO_POSITION, STOP_AND_RESET_ENCODER }
    var mode: RunMode
}

interface Servo {
    var position: Double
}

interface DigitalChannel {
    enum class Mode { INPUT, OUTPUT }
    var mode: Mode
    val state: Boolean
}

interface I2cAddr {
    companion object {
        fun create7bit(address: Int): I2cAddr = object : I2cAddr {}
        fun create8bit(address: Int): I2cAddr = object : I2cAddr {}
    }
}

interface I2cDeviceSynch {
    var i2cAddress: I2cAddr
    fun read8(register: Int): Byte
    fun read(register: Int, length: Int): ByteArray
    fun write(register: Int, data: ByteArray)
    
    var readWindow: ReadWindow
    
    class ReadWindow(val ireg: Int, val creg: Int, val mode: ReadMode)
    enum class ReadMode { REPEAT, BALANCED, ONLY_ONCE }
}

abstract class I2cDeviceSynchDevice<T>(val deviceClient: T, val isOwned: Boolean) {
    abstract fun doInitialize(): Boolean
    fun initialize(): Boolean = doInitialize()
    abstract fun getManufacturer(): Manufacturer
    abstract fun getDeviceName(): String
    
    enum class Manufacturer { Other }
}
