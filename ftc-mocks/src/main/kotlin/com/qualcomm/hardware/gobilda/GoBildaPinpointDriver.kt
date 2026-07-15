@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.gobilda

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D

open class GoBildaPinpointDriver {
    var posX: Double = 0.0
    var posY: Double = 0.0
    var heading: Double = 0.0
    var velX: Double = 0.0
    var velY: Double = 0.0
    var headingVelocity: Double = 0.0

    private var rawOffsetX: Double = 0.0
    private var rawOffsetY: Double = 0.0
    private var rawOffsetHeading: Double = 0.0
    
    fun getPosX(unit: DistanceUnit): Double {
        val dx = posX - rawOffsetX
        val dy = posY - rawOffsetY
        val cosH = kotlin.math.cos(rawOffsetHeading)
        val sinH = kotlin.math.sin(rawOffsetHeading)
        return dx * cosH - dy * sinH
    }

    fun getPosY(unit: DistanceUnit): Double {
        val dx = posX - rawOffsetX
        val dy = posY - rawOffsetY
        val cosH = kotlin.math.cos(rawOffsetHeading)
        val sinH = kotlin.math.sin(rawOffsetHeading)
        return dx * sinH + dy * cosH
    }

    fun getHeading(unit: AngleUnit): Double = heading - rawOffsetHeading
    fun getHeading(unit: UnnormalizedAngleUnit): Double = heading - rawOffsetHeading
    fun getHeadingVelocity(unit: UnnormalizedAngleUnit): Double = headingVelocity
    fun getVelX(unit: DistanceUnit): Double = velX
    fun getVelY(unit: DistanceUnit): Double = velY
    
    fun getPosition(): Pose2D {
        return Pose2D(DistanceUnit.METER, getPosX(DistanceUnit.METER), getPosY(DistanceUnit.METER), AngleUnit.RADIANS, heading - rawOffsetHeading)
    }
    
    fun update() {}
    fun resetPosAndIMU() {
        rawOffsetX = posX
        rawOffsetY = posY
        rawOffsetHeading = heading
    }

    enum class EncoderDirection { FORWARD, REVERSE }
    enum class GoBildaOdometryPods { goBilda_SWERVE_POD, goBilda_4_BAR_POD }

    fun setOffsets(xOffset: Double, yOffset: Double, unit: DistanceUnit) {}
    fun setEncoderResolution(resolution: Double, unit: DistanceUnit) {}
    fun setEncoderResolution(pod: GoBildaOdometryPods) {}
    fun setEncoderDirections(xDirection: EncoderDirection, yDirection: EncoderDirection) {}
}
