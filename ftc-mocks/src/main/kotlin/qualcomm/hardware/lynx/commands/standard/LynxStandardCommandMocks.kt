@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.lynx.commands.standard

import com.qualcomm.hardware.lynx.commands.LynxMessage
import com.qualcomm.hardware.lynx.LynxModule

open class LynxAck : LynxMessage {
    constructor(module: LynxModule, isAttentionRequired: Boolean)
}
