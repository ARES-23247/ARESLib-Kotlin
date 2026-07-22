@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.bosch

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference
import org.firstinspires.ftc.robotcore.external.navigation.Orientation

/**
 * Interface implementation for B N O055 I M U.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
interface BNO055IMU {
    /**
     * AngleUnit declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class AngleUnit { DEGREES, RADIANS }
    /**
     * Class implementation for Parameters.
     *
     * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
     */
    class Parameters {
        var angleUnit: AngleUnit = AngleUnit.RADIANS
    }
    /**
     * initialize declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun initialize(parameters: Parameters): Boolean
    /**
     * getAngularOrientation declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getAngularOrientation(reference: AxesReference, order: AxesOrder, angleUnit: org.firstinspires.ftc.robotcore.external.navigation.AngleUnit): Orientation
}
