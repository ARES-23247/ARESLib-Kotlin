package com.qualcomm.hardware.limelightvision

import org.firstinspires.ftc.robotcore.external.navigation.Pose3D

open class LLResult {
    val ta: Double = 0.0
    val tx: Double = 0.0
    val ty: Double = 0.0
    
    fun isValid(): Boolean = false
    fun getBotpose(): Pose3D? = null
}

open class Limelight3A {
    fun start() {}
    fun getLatestResult(): LLResult? = null
}

