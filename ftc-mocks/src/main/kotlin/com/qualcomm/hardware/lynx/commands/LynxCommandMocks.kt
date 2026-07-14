@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.lynx.commands

open class LynxMessage {
    val commandNumber: Int = 0
    var messageNumber: Int = 0
    var isAckable: Boolean = false
    var isResponseExpected: Boolean = false
    var destModuleAddress: Int = 0
    var serialization: LynxDatagram? = null
    var module: com.qualcomm.hardware.lynx.LynxModule? = null
}
open class LynxRespondable<T : LynxMessage> : LynxMessage() {
    open fun onResponseReceived(response: LynxMessage) {}
    open fun onAckReceived(ack: com.qualcomm.hardware.lynx.commands.standard.LynxAck) {}
}
open class LynxCommand<T : LynxMessage> : LynxRespondable<T>()
open class LynxDatagram {
    constructor() {}
    constructor(message: LynxMessage) {}
    fun toByteArray(): ByteArray = ByteArray(0)
}
