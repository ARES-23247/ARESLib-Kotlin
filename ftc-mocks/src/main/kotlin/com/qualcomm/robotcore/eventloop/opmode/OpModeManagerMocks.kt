@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.eventloop.opmode

interface OpModeManager {
    val activeOpModeName: String
    companion object {
        const val DEFAULT_OP_MODE_NAME = "\$Stop\$Robot\$"
    }
}

interface OpModeManagerNotifier {
    fun registerListener(listener: Notifications)
    fun unregisterListener(listener: Notifications)

    interface Notifications {
        fun onOpModePreInit(opMode: com.qualcomm.robotcore.eventloop.opmode.OpMode)
        fun onOpModePreStart(opMode: com.qualcomm.robotcore.eventloop.opmode.OpMode)
        fun onOpModePostStop(opMode: com.qualcomm.robotcore.eventloop.opmode.OpMode)
    }
}

open class OpModeManagerImpl : OpModeManager, OpModeManagerNotifier {
    override val activeOpModeName: String = ""
    override fun registerListener(listener: OpModeManagerNotifier.Notifications) {}
    override fun unregisterListener(listener: OpModeManagerNotifier.Notifications) {}
}
