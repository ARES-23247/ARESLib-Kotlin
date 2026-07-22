@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.lynx.commands

/**
 * Class implementation for Lynx Message.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxMessage {
    val commandNumber: Int = 0
    var messageNumber: Int = 0
    var isAckable: Boolean = false
    var isResponseExpected: Boolean = false
    var destModuleAddress: Int = 0
    var serialization: LynxDatagram? = null
    var module: com.qualcomm.hardware.lynx.LynxModule? = null
}
/**
 * Class implementation for Lynx Respondable.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxRespondable<T : LynxMessage> : LynxMessage() {
    /**
     * onResponseReceived declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun onResponseReceived(response: LynxMessage) {}
    /**
     * onAckReceived declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    open fun onAckReceived(ack: com.qualcomm.hardware.lynx.commands.standard.LynxAck) {}
}
/**
 * Class implementation for Lynx Command.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxCommand<T : LynxMessage> : LynxRespondable<T>()
/**
 * Class implementation for Lynx Datagram.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class LynxDatagram {
    constructor() {}
    constructor(message: LynxMessage) {}
    /**
     * toByteArray declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun toByteArray(): ByteArray = ByteArray(0)
}
