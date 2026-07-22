package com.areslib.control.drivetrain

import com.areslib.control.feedback.PIDController
import com.areslib.control.feedback.LinearADRC
import com.areslib.math.geometry.ChassisSpeeds
import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import kotlin.math.cos
import kotlin.math.sin

/**
 * Purely mathematical holonomic trajectory tracking controller for Mecanum and Swerve drivetrains.
 *
 * Combines velocity feedforward along the spline path tangent with PID/ADRC feedback controllers
 * for field-space translation ($x, y$) and orientation ($\theta$). Converts field-relative control efforts
 * into robot-frame [ChassisSpeeds].
 *
 * ### Control Mathematics:
 * 1. **Centripetal Velocity Throttling**:
 *    $$v_{limited} = \min\left(v_{target}, \sqrt{\frac{a_{max}}{\vert\kappa\vert}}\right)$$
 * 2. **Tangent Feedforward**:
 *    $$v_{x,FF} = v_{limited} \cos(\gamma_{tangent}), \quad v_{y,FF} = v_{limited} \sin(\gamma_{tangent})$$
 * 3. **Feedback Control (PID or ADRC)**:
 *    $$\mathbf{v}_{field} = \begin{bmatrix} v_{x,FF} + \text{Feedback}_X(x_{current}, x_{target}) \\ v_{y,FF} + \text{Feedback}_Y(y_{current}, y_{target}) \end{bmatrix}$$
 * 4. **Velocity Vector Magnitude Clamping**:
 *    If $\|\mathbf{v}_{field}\| > v_{max}$, the translational vector is scaled down to $v_{max}$ to prevent motor saturation.
 * 5. **Field-to-Robot Frame Transformation**:
 *    $$\begin{bmatrix} v_{x,robot} \\ v_{y,robot} \end{bmatrix} = \begin{bmatrix} \cos\theta & \sin\theta \\ -\sin\theta & \cos\theta \end{bmatrix} \begin{bmatrix} v_{x,field} \\ v_{y,field} \end{bmatrix}$$
 *
 * ### Physical Units:
 * - Position: Meters ($m$)
 * - Translational Velocity: Meters per second ($m/s$)
 * - Heading: Radians ($rad$), counter-clockwise positive
 * - Angular Velocity: Radians per second ($rad/s$)
 * - Time: Seconds ($s$)
 *
 * @param xController PID controller for field X-axis translation.
 * @param yController PID controller for field Y-axis translation.
 * @param thetaController PID controller for field heading rotation.
 * @param telemetry Platform telemetry backend for live diagnostic streaming (optional).
 * @param maxOutputMps Maximum translational velocity limit in meters per second (default: 4.0 m/s).
 * @param xAdrc Linear ADRC controller for field X-axis (overrides [xController] if provided).
 * @param yAdrc Linear ADRC controller for field Y-axis (overrides [yController] if provided).
 * @param thetaAdrc Linear ADRC controller for heading (overrides [thetaController] if provided).
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
     * Calculates the desired robot-frame chassis speeds based on the target pose and current pose.
     *
     * @param currentPose Current estimated robot pose on the field (units: meters, radians).
     * @param targetPose Target robot pose on the field (units: meters, radians).
     * @param targetVelocityMps Target feedforward velocity magnitude along the trajectory (units: m/s).
     * @param targetHeading Target field-relative heading orientation (units: radians).
     * @param dtSeconds Time step elapsed since last loop iteration in seconds.
     * @param pathTangentRadians Spline path derivative tangent angle (radians). Defaults to NaN (falls back to target error vector angle).
     * @param curvature Curvature of the path segment $\kappa = 1/R$ (units: $m^{-1}$).
     * @param maxCentripetalAccel Maximum centripetal acceleration threshold before speed throttling (units: $m/s^2$).
     * @param progressPercentage Percentage of path completed (0.0 to 100.0).
     *
     * @return Commanded robot-centric [ChassisSpeeds] ready for kinematics inverse calculation.
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
        val xError = targetPose.x - currentPose.x
        val yError = targetPose.y - currentPose.y

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

        val lateralError = xError * kotlin.math.sin(pathTangent) - yError * kotlin.math.cos(pathTangent)

        var angularError = targetHeading.radians - currentPose.heading.radians
        angularError = com.areslib.math.wrapAngle(angularError)

        telemetry?.let { tel ->
            tel.putNumber("PathError/LateralMeters", lateralError)
            tel.putNumber("PathError/AngularDegrees", Math.toDegrees(angularError))
            tel.putNumber("PathError/XErrorMeters", xError)
            tel.putNumber("PathError/YErrorMeters", yError)
            tel.putNumber("PathError/ProgressPercentage", progressPercentage)
        }

        var xFeedback = xAdrc?.calculate(targetPose.x, currentPose.x, dtSeconds)
            ?: xController.calculate(currentPose.x, targetPose.x, dtSeconds)

        var yFeedback = yAdrc?.calculate(targetPose.y, currentPose.y, dtSeconds)
            ?: yController.calculate(currentPose.y, targetPose.y, dtSeconds)

        val thetaFeedback = thetaAdrc?.calculate(targetHeading.radians, currentPose.heading.radians, dtSeconds)
            ?: thetaController.calculate(currentPose.heading.radians, targetHeading.radians, dtSeconds)

        val limitedVelocity = if (kotlin.math.abs(curvature) > 1e-4) {
            val maxVel = kotlin.math.sqrt(maxCentripetalAccel / kotlin.math.abs(curvature))
            kotlin.math.min(targetVelocityMps, maxVel)
        } else {
            targetVelocityMps
        }

        val xFF = limitedVelocity * cos(pathTangent)
        val yFF = limitedVelocity * sin(pathTangent)

        var fieldRelativeX = xFF + xFeedback
        var fieldRelativeY = yFF + yFeedback

        val outputMagnitude = kotlin.math.hypot(fieldRelativeX, fieldRelativeY)
        if (outputMagnitude > maxOutputMps && outputMagnitude > 1e-4) {
            val scale = maxOutputMps / outputMagnitude
            fieldRelativeX *= scale
            fieldRelativeY *= scale
        }

        return ChassisSpeeds.fromFieldRelativeSpeeds(
            fieldRelativeX,
            fieldRelativeY,
            thetaFeedback,
            currentPose.heading
        )
    }
}
