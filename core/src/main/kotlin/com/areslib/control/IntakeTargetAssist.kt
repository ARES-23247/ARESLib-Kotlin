package com.areslib.control

import com.areslib.math.ChassisSpeeds

/**
 * Intelligent Vision-based Drivetrain Steering Assist.
 * Blends manual driver inputs with real-time target tracker PID corrections
 * to automate piece pickup or scoring alignment.
 */
class IntakeTargetAssist(
    private val yawPID: PIDController = PIDController(0.04, 0.0, 0.002),
    private val lateralPID: PIDController = PIDController(0.8, 0.0, 0.05)
) {

    /**
     * Calculates the blended ChassisSpeeds incorporating manual driver commands and vision target tracking.
     *
     * @param driverManualSpeeds The raw driver inputs (x velocity, y velocity, rotational velocity).
     * @param targetVisible True if the camera currently tracks the target.
     * @param yawErrorDegrees Angular offset of the target from the robot centerline in degrees.
     * @param lateralErrorMeters Lateral translation offset of the target from the center in meters.
     * @param dtSeconds Elapsed loop time.
     */
    fun calculateAssistedSpeeds(
        driverManualSpeeds: ChassisSpeeds,
        targetVisible: Boolean,
        yawErrorDegrees: Double,
        lateralErrorMeters: Double,
        dtSeconds: Double
    ): ChassisSpeeds {
        if (!targetVisible) {
            // No target visible; return raw driver speeds unmodified
            return driverManualSpeeds
        }

        // Calculate vision-based steering and centering feedback corrections
        val rawRotCorrection = yawPID.calculate(yawErrorDegrees, 0.0, dtSeconds)
        val rawLateralCorrection = lateralPID.calculate(lateralErrorMeters, 0.0, dtSeconds)

        // Enforce tight bounds on correction limits manually
        val rotCorrection = rawRotCorrection.coerceIn(-2.0, 2.0)
        val lateralCorrection = rawLateralCorrection.coerceIn(-1.5, 1.5)

        // Blend driver's manual velocity with the closed-loop vision adjustments
        // Let the driver retain control over forward/backward velocity (vx),
        // but assist them with rotational (omega) and lateral (vy) alignment.
        val blendedVy = driverManualSpeeds.vyMetersPerSecond + lateralCorrection
        val blendedOmega = driverManualSpeeds.omegaRadiansPerSecond + rotCorrection

        return ChassisSpeeds(
            vxMetersPerSecond = driverManualSpeeds.vxMetersPerSecond,
            vyMetersPerSecond = blendedVy,
            omegaRadiansPerSecond = blendedOmega
        )
    }
}

