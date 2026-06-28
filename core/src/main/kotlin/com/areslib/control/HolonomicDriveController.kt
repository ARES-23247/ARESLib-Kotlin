package com.areslib.control

import com.areslib.math.ChassisSpeeds
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.state.Obstacle
import com.areslib.pathing.VFHPlanner
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
    private val thetaController: PIDController,
    private val telemetry: com.areslib.telemetry.ITelemetry? = null
) {
    private val vfh = VFHPlanner()

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
     * @param curvature Curvature of the path segment (1/radius).
     * @param maxCentripetalAccel Limit for lateral centripetal acceleration.
     * @param obstacles List of dynamic/static obstacles on the field.
     * @param progressPercentage Progress along the trajectory (0.0 to 100.0).
     */
    fun calculate(
        currentPose: Pose2d,
        targetPose: Pose2d,
        targetVelocityMps: Double,
        targetHeading: Rotation2d,
        dtSeconds: Double,
        curvature: Double = 0.0,
        maxCentripetalAccel: Double = 2.5,
        obstacles: List<Obstacle> = emptyList(),
        progressPercentage: Double = 0.0,
        out: ChassisSpeeds = ChassisSpeeds()
    ): ChassisSpeeds {
        // Calculate raw error components
        val xError = targetPose.x - currentPose.x
        val yError = targetPose.y - currentPose.y
        
        // Perpendicular (lateral) error calculation relative to path heading
        val pathHeading = targetPose.heading.radians
        val lateralError = xError * kotlin.math.sin(pathHeading) - yError * kotlin.math.cos(pathHeading)
        
        // Angular error normalized between -PI and PI
        var angularError = targetHeading.radians - currentPose.heading.radians
        angularError = kotlin.math.atan2(kotlin.math.sin(angularError), kotlin.math.cos(angularError))
        
        // Broadcast diagnostics via telemetry if registered
        telemetry?.let { tel ->
            tel.putNumber("PathError/LateralMeters", lateralError)
            tel.putNumber("PathError/AngularDegrees", Math.toDegrees(angularError))
            tel.putNumber("PathError/XErrorMeters", xError)
            tel.putNumber("PathError/YErrorMeters", yError)
            tel.putNumber("PathError/ProgressPercentage", progressPercentage)
        }

        // Calculate PID output for position error
        var xFeedback = xController.calculate(currentPose.x, targetPose.x, dtSeconds)
        var yFeedback = yController.calculate(currentPose.y, targetPose.y, dtSeconds)
        val thetaFeedback = thetaController.calculate(currentPose.heading.radians, targetHeading.radians, dtSeconds)

        // Calculate velocity feedforward vector
        // In a real path, the direction is derived from the path's tangent.
        // We'll approximate the path heading as the angle from current to target
        
        
        // Dynamically cap target velocity based on curve centripetal force
        val limitedVelocity = if (kotlin.math.abs(curvature) > 1e-4) {
            val maxVel = kotlin.math.sqrt(maxCentripetalAccel / kotlin.math.abs(curvature))
            kotlin.math.min(targetVelocityMps, maxVel)
        } else {
            targetVelocityMps
        }

        // Apply VFH+ to calculate dynamic steering detours
        val detourHeading = if (obstacles.isNotEmpty()) {
            vfh.computeDetourHeading(currentPose, pathHeading, obstacles)
        } else {
            pathHeading
        }

        val xFF = limitedVelocity * cos(detourHeading)
        val yFF = limitedVelocity * sin(detourHeading)

        // Sum feedforward and feedback
        var fieldRelativeX = xFF + xFeedback
        var fieldRelativeY = yFF + yFeedback

        // If detouring, project the entire desired speed vector along the safe detour direction
        if (detourHeading != pathHeading) {
            val speedMagnitude = kotlin.math.hypot(fieldRelativeX, fieldRelativeY)
            fieldRelativeX = speedMagnitude * cos(detourHeading)
            fieldRelativeY = speedMagnitude * sin(detourHeading)
        }

        // Convert field-relative speeds to robot-relative speeds
        return ChassisSpeeds.fromFieldRelativeSpeeds(
            fieldRelativeX,
            fieldRelativeY,
            thetaFeedback,
            currentPose.heading,
            out
        )
    }
}
