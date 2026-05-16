package com.areslib.control

import com.areslib.math.ChassisSpeeds
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import kotlin.math.cos
import kotlin.math.sin

/**
 * A purely mathematical implementation of a holonomic drive controller.
 * Given a target pose (e.g. from a PathPlanner point) and current pose,
 * outputs ChassisSpeeds that can be fed into mecanum kinematics.
 */
class HolonomicDriveController(
    private val xController: PIDController,
    private val yController: PIDController,
    private val thetaController: PIDController
) {
    init {
        thetaController.enableContinuousInput(-Math.PI, Math.PI)
    }

    /**
     * Calculates the desired chassis speeds based on target pose and current pose.
     * @param currentPose Current robot pose on the field.
     * @param targetPose Desired robot pose on the field.
     * @param targetVelocityMps Feedforward velocity along the path.
     * @param targetHeading The desired field-relative heading to face.
     * @param dtSeconds Time elapsed since last call.
     */
    fun calculate(
        currentPose: Pose2d,
        targetPose: Pose2d,
        targetVelocityMps: Double,
        targetHeading: Rotation2d,
        dtSeconds: Double
    ): ChassisSpeeds {
        // Calculate PID output for position error
        var xFeedback = xController.calculate(currentPose.x, targetPose.x, dtSeconds)
        var yFeedback = yController.calculate(currentPose.y, targetPose.y, dtSeconds)
        val thetaFeedback = thetaController.calculate(currentPose.heading.radians, targetHeading.radians, dtSeconds)

        // Calculate velocity feedforward vector
        // In a real path, the direction is derived from the path's tangent.
        // We'll approximate the path heading as the angle from current to target
        val dx = targetPose.x - currentPose.x
        val dy = targetPose.y - currentPose.y
        val pathHeading = if (dx == 0.0 && dy == 0.0) 0.0 else kotlin.math.atan2(dy, dx)
        
        val xFF = targetVelocityMps * cos(pathHeading)
        val yFF = targetVelocityMps * sin(pathHeading)

        // Sum feedforward and feedback
        val fieldRelativeX = xFF + xFeedback
        val fieldRelativeY = yFF + yFeedback

        // Convert field-relative speeds to robot-relative speeds
        return ChassisSpeeds.fromFieldRelativeSpeeds(
            fieldRelativeX,
            fieldRelativeY,
            thetaFeedback,
            currentPose.heading
        )
    }
}
