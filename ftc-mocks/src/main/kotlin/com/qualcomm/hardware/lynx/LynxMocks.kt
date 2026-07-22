@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.lynx

import com.qualcomm.hardware.lynx.commands.LynxMessage
import com.qualcomm.hardware.lynx.commands.LynxRespondable
import java.util.concurrent.ConcurrentHashMap

/**
 * Class implementation for Lynx Module.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxModule(
    val lynxUsbDevice: LynxUsbDevice?,
    val moduleAddress: Int,
    val isParent: Boolean,
    val isUserModule: Boolean
) : com.qualcomm.robotcore.hardware.HardwareDevice {
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
    override fun getManufacturer(): com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer = com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer.Unknown
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
    override fun getDeviceName(): String = ""
    /**
     * getConnectionInfo declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getConnectionInfo(): String = ""
    /**
     * getVersion declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getVersion(): Int = 1
    /**
     * resetDeviceConfigurationForOpMode declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun resetDeviceConfigurationForOpMode() {}
    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {}

    /**
     * BulkCachingMode declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class BulkCachingMode { OFF, AUTO, MANUAL }
    var bulkCachingMode = BulkCachingMode.OFF
    val serialNumber = com.qualcomm.robotcore.util.SerialNumber("")
    /**
     * clearBulkCache declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun clearBulkCache() {}
    /**
     * getNewMessageNumber declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun getNewMessageNumber(): Byte = 0
    /**
     * sendCommand declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun sendCommand(command: LynxMessage) {}
    /**
     * acquireNetworkTransmissionLock declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun acquireNetworkTransmissionLock(message: LynxMessage) {}
    /**
     * releaseNetworkTransmissionLock declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun releaseNetworkTransmissionLock(message: LynxMessage) {}
    /**
     * removeConfiguredModule declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun removeConfiguredModule(module: com.qualcomm.robotcore.hardware.HardwareDevice) {}
    var unfinishedCommands = ConcurrentHashMap<Int, LynxRespondable<LynxMessage>>()
}

/**
 * Class implementation for Lynx I2c Device Synch.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxI2cDeviceSynch
/**
 * Class implementation for Lynx Unsupported Command Exception.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class LynxUnsupportedCommandException : Exception()
/**
 * Class implementation for Lynx Usb Device.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxUsbDevice {
    /**
     * removeConfiguredModule declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun removeConfiguredModule(module: LynxModule) {}
}
/**
 * Class implementation for Lynx Usb Device Delegate.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxUsbDeviceDelegate : LynxUsbDevice()
/**
 * Class implementation for Lynx Usb Device Impl.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxUsbDeviceImpl : LynxUsbDeviceDelegate()
