@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.lynx.commands.core

import com.qualcomm.hardware.lynx.commands.LynxCommand
import com.qualcomm.hardware.lynx.commands.standard.LynxAck

open class LynxSetMotorConstantPowerCommand : LynxCommand<LynxAck>()
open class LynxSetServoPulseWidthCommand : LynxCommand<LynxAck>()
