@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.hardware.configuration.annotations

annotation class DeviceProperties(
    val name: String,
    val xmlTag: String,
    val description: String
)

annotation class I2cDeviceType
