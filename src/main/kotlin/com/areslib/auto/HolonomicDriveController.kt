package com.areslib.auto

import com.areslib.math.ChassisSpeeds
import com.areslib.math.Translation2d
import com.areslib.math.Rotation2d

class HolonomicDriveController(
    private val kPTranslation: Double,
    private val kPRotation: Double
) {

    /**
     * Pure function to calculate target ChassisSpeeds given current state and target state.
     */
    fun calculate(
        currentPose: Translation2d,
        currentHeading: Rotation2d,
        targetState: TrajectoryState
    ): ChassisSpeeds {
        
        val xError = targetState.poseMeters.x - currentPose.x
        val yError = targetState.poseMeters.y - currentPose.y
        val headingError = targetState.headingRadians.radians - currentHeading.radians

        // Feedforward + Proportional feedback
        val vx = targetState.velocityMetersPerSecond * targetState.headingRadians.cos + (kPTranslation * xError)
        val vy = targetState.velocityMetersPerSecond * targetState.headingRadians.sin + (kPTranslation * yError)
        val omega = kPRotation * headingError

        return ChassisSpeeds(vx, vy, omega)
    }
}
