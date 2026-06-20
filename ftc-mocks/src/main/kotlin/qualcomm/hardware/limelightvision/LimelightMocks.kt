package com.qualcomm.hardware.limelightvision

import org.firstinspires.ftc.robotcore.external.navigation.Pose3D

open class LLResultTypes {
    open class FiducialResult(
        private val fiducialId: Int,
        private val tx: Double,
        private val ty: Double,
        private val pose3d: Pose3D,
        private val robotPoseTargetSpace: Pose3D = Pose3D()
    ) {
        open fun getFiducialId(): Int = fiducialId
        open fun getTx(): Double = tx
        open fun getTy(): Double = ty
        open fun getPose3D(): Pose3D = pose3d
        open fun getRobotPoseTargetSpace(): Pose3D = robotPoseTargetSpace
    }
}

open class LLResult {
    val ta: Double = 0.0
    val tx: Double = 0.0
    val ty: Double = 0.0
    
    open fun isValid(): Boolean = false
    open fun getBotpose(): Pose3D? = null
    open fun getFiducialResults(): List<LLResultTypes.FiducialResult> = emptyList()
}

open class Limelight3A {
    open fun start() {}
    open fun getLatestResult(): LLResult? = null
}

