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
 *
 * @param maxOutputMps Maximum translational output velocity magnitude (m/s).
 *        The combined feedforward + feedback output is clamped to this value
 *        to prevent commanding speeds beyond the robot's physical capabilities.
 */
class HolonomicDriveController(
    private val xController: PIDController,
    private val yController: PIDController,
    private val thetaController: PIDController,
    private val telemetry: com.areslib.telemetry.ITelemetry? = null,
    private val maxOutputMps: Double = 4.0,
    private val xAdrc: LinearADRC? = null,
    private val yAdrc: LinearADRC? = null,
    private val thetaAdrc: LinearADRC? = null
) {

    init {
        thetaController.enableContinuousInput(-Math.PI, Math.PI)
        thetaAdrc?.enableContinuousInput(-Math.PI, Math.PI)
    }

    /**
     * Calculates the desired chassis speeds based on target pose and current pose.
     * @param currentPose Current robot pose on the field.
     * @param targetPose Desired robot pose on the field.
     * @param targetVelocityMps Feedforward velocity along the path.
     * @param targetHeading The desired field-relative heading to face.
     * @param dtSeconds Time elapsed since last call.
     * @param pathTangentRadians The direction the path is traveling at the current sample point.
     *        This is the spline derivative direction and is distinct from [targetHeading]
     *        (the robot's desired orientation). If not provided, falls back to the angle
     *        from the current position to the target position.
     * @param curvature Curvature of the path segment (1/radius).
     * @param maxCentripetalAccel Limit for lateral centripetal acceleration.
     * @param progressPercentage Progress along the trajectory (0.0 to 100.0).
     */
    fun calculate(
        currentPose: Pose2d,
        targetPose: Pose2d,
        targetVelocityMps: Double,
        targetHeading: Rotation2d,
        dtSeconds: Double,
        pathTangentRadians: Double = Double.NaN,
        curvature: Double = 0.0,
        maxCentripetalAccel: Double = 2.5,
        progressPercentage: Double = 0.0
    ): ChassisSpeeds {
        // Calculate raw error components
        val xError = targetPose.x - currentPose.x
        val yError = targetPose.y - currentPose.y
        
        // Use the explicit spline tangent for feedforward direction.
        // Only fall back to error-based direction if no tangent was provided.
        val pathTangent = if (!pathTangentRadians.isNaN()) {
            pathTangentRadians
        } else {
            val distanceToTarget = kotlin.math.hypot(xError, yError)
            if (distanceToTarget > 0.01) {
                kotlin.math.atan2(yError, xError)
            } else {
                targetHeading.radians
            }
        }
        
        // Perpendicular (lateral) error calculation relative to path tangent
        val lateralError = xError * kotlin.math.sin(pathTangent) - yError * kotlin.math.cos(pathTangent)
        
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

        // Calculate output for position error (ADRC overrides PID if provided)
        var xFeedback = xAdrc?.calculate(targetPose.x, currentPose.x, dtSeconds)
            ?: xController.calculate(currentPose.x, targetPose.x, dtSeconds)
            
        var yFeedback = yAdrc?.calculate(targetPose.y, currentPose.y, dtSeconds)
            ?: yController.calculate(currentPose.y, targetPose.y, dtSeconds)
            
        val thetaFeedback = thetaAdrc?.calculate(targetHeading.radians, currentPose.heading.radians, dtSeconds)
            ?: thetaController.calculate(currentPose.heading.radians, targetHeading.radians, dtSeconds)

        // Dynamically cap target velocity based on curve centripetal force
        val limitedVelocity = if (kotlin.math.abs(curvature) > 1e-4) {
            val maxVel = kotlin.math.sqrt(maxCentripetalAccel / kotlin.math.abs(curvature))
            kotlin.math.min(targetVelocityMps, maxVel)
        } else {
            targetVelocityMps
        }

        // Velocity feedforward along the path tangent
        val xFF = limitedVelocity * cos(pathTangent)
        val yFF = limitedVelocity * sin(pathTangent)

        // Sum feedforward and feedback
        var fieldRelativeX = xFF + xFeedback
        var fieldRelativeY = yFF + yFeedback

        // Clamp total velocity to physical limits
        val outputMagnitude = kotlin.math.hypot(fieldRelativeX, fieldRelativeY)
        if (outputMagnitude > maxOutputMps && outputMagnitude > 1e-4) {
            val scale = maxOutputMps / outputMagnitude
            fieldRelativeX *= scale
            fieldRelativeY *= scale
        }

        // Convert field-relative speeds to robot-relative speeds
        return ChassisSpeeds.fromFieldRelativeSpeeds(
            fieldRelativeX,
            fieldRelativeY,
            thetaFeedback,
            currentPose.heading
        )
    }
}

