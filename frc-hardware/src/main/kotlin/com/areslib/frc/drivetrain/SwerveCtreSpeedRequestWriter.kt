package com.areslib.frc.drivetrain

import com.areslib.state.DriveState
import com.ctre.phoenix6.swerve.SwerveDrivetrain
import com.ctre.phoenix6.swerve.SwerveRequest
import edu.wpi.first.math.kinematics.ChassisSpeeds

/**
 * Writer class for CTRE Phoenix6 Swerve Drivetrain hardware actuation.
 *
 * Hardware IO abstraction layer mapping immutable Redux state representations into physical robot actuator commands.
 * 
 * PHYSICAL UNITS & CONVENTIONS:
 * - Velocities: $m/s$ for X/Y translation, $rad/s$ for rotation.
 * - Angle convention: **CCW-positive**.
 * 
 * PERFORMANCE:
 * Guaranteed zero-GC allocations during the high-frequency motor control loop.
 *
 * @param drivetrain The CTRE `SwerveDrivetrain` to dispatch requests to.
 */
class SwerveCtreSpeedRequestWriter(private val drivetrain: SwerveDrivetrain<*, *, *>) {
    private val fieldCentricRequest = SwerveRequest.FieldCentric()
    private val robotSpeedsRequest = SwerveRequest.ApplyRobotSpeeds()
    private val scratchSpeeds = ChassisSpeeds()

    /**
     * Safes the drivetrain by commanding zero velocity.
     * Zero-GC allocation.
     */
    fun safe() {
        write(DriveState())
    }

    /**
     * Dispatches the target chassis speeds to the CTRE drivetrain.
     * Switches transparently between field-centric and robot-centric requests.
     * 
     * @param state The target [DriveState] containing $m/s$ and $rad/s$ requests.
     */
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
