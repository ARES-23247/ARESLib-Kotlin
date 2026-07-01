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
    
    fun getPosX(unit: DistanceUnit): Double = posX - rawOffsetX
    fun getPosY(unit: DistanceUnit): Double = posY - rawOffsetY
    fun getHeading(unit: AngleUnit): Double = heading - rawOffsetHeading
    fun getHeading(unit: UnnormalizedAngleUnit): Double = heading - rawOffsetHeading
    fun getHeadingVelocity(unit: UnnormalizedAngleUnit): Double = headingVelocity
    fun getVelX(unit: DistanceUnit): Double = velX
    fun getVelY(unit: DistanceUnit): Double = velY
    
    fun getPosition(): Pose2D {
        return Pose2D(DistanceUnit.METER, posX - rawOffsetX, posY - rawOffsetY, AngleUnit.RADIANS, heading - rawOffsetHeading)
    }
    
    fun update() {}
    fun resetPosAndIMU() {
        rawOffsetX = posX
        rawOffsetY = posY
        rawOffsetHeading = heading
    }
}
