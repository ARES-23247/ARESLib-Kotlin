package com.areslib.ftc.photon

import com.qualcomm.hardware.lynx.LynxModule
import com.qualcomm.hardware.lynx.LynxUnsupportedCommandException
import com.qualcomm.hardware.lynx.LynxUsbDevice
import com.qualcomm.hardware.lynx.commands.LynxCommand
import com.qualcomm.hardware.lynx.commands.LynxMessage
import com.qualcomm.hardware.lynx.commands.LynxRespondable
import java.util.concurrent.ConcurrentHashMap

class AresPhotonLynxModule(
    lynxUsbDevice: LynxUsbDevice?,
    moduleAddress: Int,
    isParent: Boolean,
    isUserModule: Boolean
) : LynxModule(lynxUsbDevice, moduleAddress, isParent, isUserModule) {

    private val skippedAcquire = ArrayList<LynxMessage>()

    fun getUnfinishedCommandsMap(): ConcurrentHashMap<Int, LynxRespondable<LynxMessage>> {
        @Suppress("UNCHECKED_CAST")
        return this.unfinishedCommands as ConcurrentHashMap<Int, LynxRespondable<LynxMessage>>
    }

    override fun getNewMessageNumber(): Byte {
        return super.getNewMessageNumber()
    }

    @Throws(InterruptedException::class, LynxUnsupportedCommandException::class)
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
