@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.gobilda

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D

open class GoBildaPinpointDriver {
    @Volatile var posX: Double = 0.0
    @Volatile var posY: Double = 0.0
    @Volatile var heading: Double = 0.0
    @Volatile var trueHeading: Double = 0.0
    @Volatile var velX: Double = 0.0
    @Volatile var velY: Double = 0.0
    @Volatile var headingVelocity: Double = 0.0

    @Volatile private var rawOffsetX: Double = 0.0
    @Volatile private var rawOffsetY: Double = 0.0
    @Volatile private var rawOffsetHeading: Double = 0.0
    @Volatile private var trueOffsetHeading: Double = 0.0
    
    fun getPosX(unit: DistanceUnit): Double {
        val dx = posX - rawOffsetX
        val dy = posY - rawOffsetY
        // Rotate world delta by -trueOffsetHeading to get local X displacement
        val cosH = kotlin.math.cos(trueOffsetHeading)
        val sinH = kotlin.math.sin(trueOffsetHeading)
        return dx * cosH + dy * sinH
    }

    fun getPosY(unit: DistanceUnit): Double {
        val dx = posX - rawOffsetX
        val dy = posY - rawOffsetY
        // Rotate world delta by -trueOffsetHeading to get local Y displacement
        val cosH = kotlin.math.cos(trueOffsetHeading)
        val sinH = kotlin.math.sin(trueOffsetHeading)
        return -dx * sinH + dy * cosH
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
        trueOffsetHeading = trueHeading
    }
    fun recalibrateIMU() {}

    enum class EncoderDirection { FORWARD, REVERSE }
    enum class GoBildaOdometryPods { goBilda_SWERVE_POD, goBilda_4_BAR_POD }

    fun setOffsets(xOffset: Double, yOffset: Double, unit: DistanceUnit) {}
    fun setEncoderResolution(resolution: Double, unit: DistanceUnit) {}
    fun setEncoderResolution(pod: GoBildaOdometryPods) {}
    fun setEncoderDirections(xDirection: EncoderDirection, yDirection: EncoderDirection) {}
}
