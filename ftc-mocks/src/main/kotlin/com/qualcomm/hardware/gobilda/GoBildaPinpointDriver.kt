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
    
    fun getPosX(unit: DistanceUnit): Double = posX
    fun getPosY(unit: DistanceUnit): Double = posY
    fun getHeading(unit: AngleUnit): Double = heading
    fun getHeading(unit: UnnormalizedAngleUnit): Double = heading
    fun getHeadingVelocity(unit: UnnormalizedAngleUnit): Double = headingVelocity
    fun getVelX(unit: DistanceUnit): Double = velX
    fun getVelY(unit: DistanceUnit): Double = velY
    
    fun getPosition(): Pose2D {
        return Pose2D(DistanceUnit.METER, posX, posY, AngleUnit.RADIANS, heading)
    }
    
    fun update() {}
    fun resetPosAndIMU() {}
}
