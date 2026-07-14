@file:Suppress("UNUSED_PARAMETER")
package org.firstinspires.ftc.robotcore.internal.usb.exception

class RobotUsbException(message: String) : Exception(message) {
    constructor() : this("")
}
