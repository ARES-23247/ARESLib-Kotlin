package com.qualcomm.robotcore.hardware

/**
 * Mock representation of an FTC [I2cAddr].
 */
interface I2cAddr {
    companion object {
        fun create7bit(address: Int): I2cAddr = object : I2cAddr {}
        fun create8bit(address: Int): I2cAddr = object : I2cAddr {}
    }
}

/**
 * Mock representation of an FTC [I2cDeviceSynch].
 */
interface I2cDeviceSynch : I2cDeviceSynchSimple {
    var i2cAddress: I2cAddr
    /**
     * read8 declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun read8(register: Int): Byte
    /**
     * read declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun read(register: Int, length: Int): ByteArray
    /**
     * write declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun write(register: Int, data: ByteArray)
    
    var readWindow: ReadWindow
    
    /**
     * Class implementation for Read Window.
     *
     * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
     */
    class ReadWindow(val ireg: Int, val creg: Int, val mode: ReadMode)
    /**
     * ReadMode declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class ReadMode { REPEAT, BALANCED, ONLY_ONCE }
}

/**
 * Mock representation of an FTC [I2cDeviceSynchDevice].
 */
abstract class I2cDeviceSynchDevice<T>(val deviceClient: T, val isOwned: Boolean) where T : I2cDeviceSynchSimple {
    /**
     * doInitialize declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    abstract fun doInitialize(): Boolean
    /**
     * initialize declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun initialize(): Boolean = doInitialize()
    /**
     * getManufacturer declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    abstract fun getManufacturer(): Manufacturer
    /**
     * getDeviceName declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    abstract fun getDeviceName(): String

    /**
     * Manufacturer declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class Manufacturer { Other, Unknown, Lynx, Rev, Broadcom, Adafruit }
}
