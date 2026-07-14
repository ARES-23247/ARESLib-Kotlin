@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.bosch

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference
import org.firstinspires.ftc.robotcore.external.navigation.Orientation

interface BNO055IMU {
    enum class AngleUnit { DEGREES, RADIANS }
    class Parameters {
        var angleUnit: AngleUnit = AngleUnit.RADIANS
    }
    fun initialize(parameters: Parameters): Boolean
    fun getAngularOrientation(reference: AxesReference, order: AxesOrder, angleUnit: org.firstinspires.ftc.robotcore.external.navigation.AngleUnit): Orientation
}
