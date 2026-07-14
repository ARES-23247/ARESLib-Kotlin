@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.lynx

import com.qualcomm.hardware.lynx.commands.LynxMessage
import com.qualcomm.hardware.lynx.commands.LynxRespondable
import java.util.concurrent.ConcurrentHashMap

open class LynxModule(
    val lynxUsbDevice: LynxUsbDevice?,
    val moduleAddress: Int,
    val isParent: Boolean,
    val isUserModule: Boolean
) : com.qualcomm.robotcore.hardware.HardwareDevice {
    override fun getManufacturer(): com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer = com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer.Unknown
    override fun getDeviceName(): String = ""
    override fun getConnectionInfo(): String = ""
    override fun getVersion(): Int = 1
    override fun resetDeviceConfigurationForOpMode() {}
    override fun close() {}

    enum class BulkCachingMode { OFF, AUTO, MANUAL }
    var bulkCachingMode = BulkCachingMode.OFF
    val serialNumber = com.qualcomm.robotcore.util.SerialNumber("")
    open fun clearBulkCache() {}
    open fun getNewMessageNumber(): Byte = 0
    open fun sendCommand(command: LynxMessage) {}
    open fun acquireNetworkTransmissionLock(message: LynxMessage) {}
    open fun releaseNetworkTransmissionLock(message: LynxMessage) {}
    open fun removeConfiguredModule(module: com.qualcomm.robotcore.hardware.HardwareDevice) {}
    var unfinishedCommands = ConcurrentHashMap<Int, LynxRespondable<LynxMessage>>()
}

open class LynxI2cDeviceSynch
class LynxUnsupportedCommandException : Exception()
open class LynxUsbDevice {
    open fun removeConfiguredModule(module: LynxModule) {}
}
open class LynxUsbDeviceDelegate : LynxUsbDevice()
open class LynxUsbDeviceImpl : LynxUsbDeviceDelegate()
