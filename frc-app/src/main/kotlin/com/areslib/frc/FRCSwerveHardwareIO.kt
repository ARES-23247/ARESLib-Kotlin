package com.areslib.frc

import com.areslib.state.DriveState
import com.ctre.phoenix6.swerve.SwerveDrivetrain
import com.ctre.phoenix6.swerve.SwerveRequest
import edu.wpi.first.math.kinematics.ChassisSpeeds

/**
 * Hardware IO bridge for an FRC Phoenix 6 Swerve Drive.
 * 
 * This class abstracts the highly-optimized CTRE SwerveDrivetrain (which runs internally
 * at 250Hz on the CAN FD bus) into the pure mathematical ARESLib Redux architecture.
 */
class FRCSwerveHardwareIO(private val drivetrain: SwerveDrivetrain<*, *, *>) {

    // CTRE Swerve request object we will mutate every loop
    private val robotSpeedsRequest = SwerveRequest.ApplyRobotSpeeds()

    /**
     * Reads the 250Hz synchronized pose from the CTRE drivetrain and maps it
     * into the Redux DriveState object for the next calculation cycle.
     */
    fun read(): DriveState {
        val driveStateObj = drivetrain.state
        val pose = driveStateObj.Pose

        return DriveState(
            // We read the actual measured velocities from the drivetrain state
            xVelocityMetersPerSecond = driveStateObj.Speeds.vxMetersPerSecond,
            yVelocityMetersPerSecond = driveStateObj.Speeds.vyMetersPerSecond,
            angularVelocityRadiansPerSecond = driveStateObj.Speeds.omegaRadiansPerSecond,
            
            // Map the internal 250Hz odometry to the Redux state
            odometryX = pose.x,
            odometryY = pose.y,
            odometryHeading = pose.rotation.radians
        )
    }

    /**
     * Applies the macro-level ChassisSpeeds computed by the Redux reducers 
     * back to the CTRE SwerveDrivetrain. The CTRE firmware handles the inverse 
     * kinematics and closed-loop motor control internally.
     */
    fun write(driveState: DriveState) {
        val speeds = ChassisSpeeds(
            driveState.xVelocityMetersPerSecond,
            driveState.yVelocityMetersPerSecond,
            driveState.angularVelocityRadiansPerSecond
        )
        
        // Pass the speeds to the CTRE API
        drivetrain.setControl(robotSpeedsRequest.withSpeeds(speeds))
    }
}
