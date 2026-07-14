@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.hardware.configuration

object LynxConstants {
    const val INITIAL_MOTOR_PORT = 0
    const val NUMBER_OF_MOTORS = 4
    const val INITIAL_SERVO_PORT = 0
    const val NUMBER_OF_SERVOS = 6
    fun isEmbeddedSerialNumber(serialNumber: com.qualcomm.robotcore.util.SerialNumber): Boolean = false
}
