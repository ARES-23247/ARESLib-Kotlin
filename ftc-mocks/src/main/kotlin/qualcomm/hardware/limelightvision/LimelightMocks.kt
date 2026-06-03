package com.qualcomm.hardware.limelightvision

import org.firstinspires.ftc.robotcore.external.navigation.Pose3D

open class LLResult {
    val ta: Double = 0.0
    val tx: Double = 0.0
    val ty: Double = 0.0
    
    open fun isValid(): Boolean = false
    open fun getBotpose(): Pose3D? = null
}

open class Limelight3A {
    open fun start() {}
    open fun getLatestResult(): LLResult? = null
}

