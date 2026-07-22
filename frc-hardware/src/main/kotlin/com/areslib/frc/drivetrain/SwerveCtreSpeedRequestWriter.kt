package com.areslib.frc.drivetrain

import com.areslib.state.DriveState
import com.ctre.phoenix6.swerve.SwerveDrivetrain
import com.ctre.phoenix6.swerve.SwerveRequest
import edu.wpi.first.math.kinematics.ChassisSpeeds

class SwerveCtreSpeedRequestWriter(private val drivetrain: SwerveDrivetrain<*, *, *>) {
    private val fieldCentricRequest = SwerveRequest.FieldCentric()
    private val robotSpeedsRequest = SwerveRequest.ApplyRobotSpeeds()
    private val scratchSpeeds = ChassisSpeeds()

    fun safe() {
        write(DriveState())
    }

    fun write(state: DriveState) {
        if (state.isFieldCentric) {
            fieldCentricRequest.VelocityX = state.xVelocityMetersPerSecond
            fieldCentricRequest.VelocityY = state.yVelocityMetersPerSecond
            fieldCentricRequest.RotationalRate = state.angularVelocityRadiansPerSecond
            drivetrain.setControl(fieldCentricRequest)
        } else {
            scratchSpeeds.vxMetersPerSecond = state.xVelocityMetersPerSecond
            scratchSpeeds.vyMetersPerSecond = state.yVelocityMetersPerSecond
            scratchSpeeds.omegaRadiansPerSecond = state.angularVelocityRadiansPerSecond
            robotSpeedsRequest.Speeds = scratchSpeeds
            drivetrain.setControl(robotSpeedsRequest)
        }
    }
}
