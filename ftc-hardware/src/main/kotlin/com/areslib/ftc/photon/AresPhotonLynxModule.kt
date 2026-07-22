package com.areslib.ftc.photon

import com.qualcomm.hardware.lynx.LynxModule
import com.qualcomm.hardware.lynx.LynxUnsupportedCommandException
import com.qualcomm.hardware.lynx.LynxUsbDevice
import com.qualcomm.hardware.lynx.commands.LynxCommand
import com.qualcomm.hardware.lynx.commands.LynxMessage
import com.qualcomm.hardware.lynx.commands.LynxRespondable
import java.util.concurrent.ConcurrentHashMap

/**
 * Class implementation for Ares Photon Lynx Module.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class AresPhotonLynxModule(
    lynxUsbDevice: LynxUsbDevice?,
    moduleAddress: Int,
    isParent: Boolean,
    isUserModule: Boolean
) : LynxModule(lynxUsbDevice, moduleAddress, isParent, isUserModule) {

    private val skippedAcquire = ArrayList<LynxMessage>()

    /**
     * getUnfinishedCommandsMap declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getUnfinishedCommandsMap(): ConcurrentHashMap<Int, LynxRespondable<LynxMessage>> {
        @Suppress("UNCHECKED_CAST")
        return this.unfinishedCommands as ConcurrentHashMap<Int, LynxRespondable<LynxMessage>>
    }

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
    override fun getNewMessageNumber(): Byte {
        return super.getNewMessageNumber()
    }

    @Throws(InterruptedException::class, LynxUnsupportedCommandException::class)
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
    override fun sendCommand(command: LynxMessage) {
        if (!AresPhotonCore.isEnabled.get()) {
            super.sendCommand(command)
            return
        }
        if (command is LynxCommand<*>) {
            if (AresPhotonCore.getCacheResponse(command) != null) {
                @Suppress("UNCHECKED_CAST")
                (command as LynxCommand<LynxMessage>).onResponseReceived(AresPhotonCore.getCacheResponse(command)!!)
                return
            }
            if (AresPhotonCore.shouldParallelize(command)) {
                val success = AresPhotonCore.registerSend(command)
                if (!success) {
                    super.sendCommand(command)
                }
                return
            }
        }
        super.sendCommand(command)
    }

    @Throws(InterruptedException::class)
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
    override fun acquireNetworkTransmissionLock(message: LynxMessage) {
        if (!AresPhotonCore.isEnabled.get()) {
            super.acquireNetworkTransmissionLock(message)
            return
        }
        if (message is LynxCommand<*>) {
            if (AresPhotonCore.getCacheResponse(message) != null) {
                skippedAcquire.add(message)
                return
            }
            if (AresPhotonCore.shouldParallelize(message)) {
                skippedAcquire.add(message)
                return
            }
        }
        super.acquireNetworkTransmissionLock(message)
    }

    @Throws(InterruptedException::class)
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
    override fun releaseNetworkTransmissionLock(message: LynxMessage) {
        if (!AresPhotonCore.isEnabled.get()) {
            super.releaseNetworkTransmissionLock(message)
            return
        }
        if (skippedAcquire.contains(message)) {
            skippedAcquire.remove(message)
            return
        }
        super.releaseNetworkTransmissionLock(message)
    }
}
