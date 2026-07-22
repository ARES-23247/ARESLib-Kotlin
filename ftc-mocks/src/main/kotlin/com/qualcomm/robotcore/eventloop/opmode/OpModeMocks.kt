@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.eventloop.opmode

import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry

/**
 * Class implementation for Op Mode.
 *
 * Robotics framework control component.
 */
abstract class OpMode {
    var hardwareMap: HardwareMap = HardwareMap()
    var telemetry: Telemetry = org.firstinspires.ftc.robotcore.external.MockTelemetry()

    /**
     * init declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    abstract fun init()
    /**
     * loop declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    abstract fun loop()
}
