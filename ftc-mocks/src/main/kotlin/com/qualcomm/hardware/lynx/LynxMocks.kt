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
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getManufacturer(): com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer = com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer.Unknown
    /**
     * getDeviceName declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getDeviceName(): String = ""
    /**
     * getConnectionInfo declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getConnectionInfo(): String = ""
    /**
     * getVersion declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getVersion(): Int = 1
    /**
     * resetDeviceConfigurationForOpMode declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun resetDeviceConfigurationForOpMode() {}
    /**
     * close declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {}

    /**
     * BulkCachingMode declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class BulkCachingMode { OFF, AUTO, MANUAL }
    var bulkCachingMode = BulkCachingMode.OFF
    val serialNumber = com.qualcomm.robotcore.util.SerialNumber("")
    /**
     * clearBulkCache declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun clearBulkCache() {}
    /**
     * getNewMessageNumber declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun getNewMessageNumber(): Byte = 0
    /**
     * sendCommand declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun sendCommand(command: LynxMessage) {}
    /**
     * acquireNetworkTransmissionLock declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun acquireNetworkTransmissionLock(message: LynxMessage) {}
    /**
     * releaseNetworkTransmissionLock declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun releaseNetworkTransmissionLock(message: LynxMessage) {}
    /**
     * removeConfiguredModule declaration.
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
