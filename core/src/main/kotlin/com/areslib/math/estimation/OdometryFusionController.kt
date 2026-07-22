package com.areslib.math.estimation

import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import com.areslib.math.geometry.Translation2d
import com.areslib.math.geometry.Matrix3x3

object OdometryFusionController {
    fun processOdometry(
        state: PoseEstimatorState,
        timestampMs: Long,
        deltaTranslation: Translation2d,
        deltaHeading: Rotation2d,
        pitchDegrees: Double,
        rollDegrees: Double,
        pitchVelocityDegPerSec: Double,
        rollVelocityDegPerSec: Double,
        gyroRateRadPerSec: Double,
        dtSeconds: Double,
        baseQ: Matrix3x3,
        scratchQ: Matrix3x3,
        scratchCov: Matrix3x3
    ): PoseEstimatorState {
        if (deltaTranslation.x.isNaN() || deltaTranslation.x.isInfinite() ||
            deltaTranslation.y.isNaN() || deltaTranslation.y.isInfinite() ||
            deltaHeading.radians.isNaN() || deltaHeading.radians.isInfinite() ||
            pitchDegrees.isNaN() || pitchDegrees.isInfinite() ||
            rollDegrees.isNaN() || rollDegrees.isInfinite() ||
            pitchVelocityDegPerSec.isNaN() || pitchVelocityDegPerSec.isInfinite() ||
            rollVelocityDegPerSec.isNaN() || rollVelocityDegPerSec.isInfinite() ||
            gyroRateRadPerSec.isNaN() || gyroRateRadPerSec.isInfinite() ||
            dtSeconds.isNaN() || dtSeconds.isInfinite()
        ) {
            return state
        }

        val tiltDegrees = kotlin.math.sqrt(pitchDegrees * pitchDegrees + rollDegrees * rollDegrees)
        val tiltVelocity = kotlin.math.sqrt(pitchVelocityDegPerSec * pitchVelocityDegPerSec + rollVelocityDegPerSec * rollVelocityDegPerSec)

        var currentlyBeached = state.isBeached
        var unbeachedTime = state.lastUnbeachedTimeMs

        // Hysteresis logic
        when {
            !currentlyBeached && tiltDegrees > 15.0 -> {
                currentlyBeached = true
            }
            currentlyBeached && tiltDegrees < 12.0 -> {
                currentlyBeached = false
                unbeachedTime = timestampMs
            }
        }

        // Catastrophic tilt / beaching check: Freeze odometry updates
        if (currentlyBeached) {
            val poseForHistory = Pose2d(state.estimatedPoseX, state.estimatedPoseY, Rotation2d(state.estimatedPoseHeading))
            val covForHistory = Matrix3x3(
                state.covarianceArray[0], state.covarianceArray[1], state.covarianceArray[2],
                state.covarianceArray[3], state.covarianceArray[4], state.covarianceArray[5],
                state.covarianceArray[6], state.covarianceArray[7], state.covarianceArray[8]
            )
            state.history.addEntry(timestampMs, poseForHistory, covForHistory, 1.0)
            state.isBeached = true
            state.lastUnbeachedTimeMs = unbeachedTime
            return state
        }

        val timeSinceUnbeachedMs = timestampMs - unbeachedTime
        val inRecovery = timeSinceUnbeachedMs < 500 && unbeachedTime != 0L

        // Continuous covariance scaling
        var tiltScale = 1.0
        if (tiltDegrees > 5.0) {
            val normalized = (tiltDegrees - 5.0) / 10.0 // 0.0 to 1.0
            val clamped = normalized.coerceIn(0.0, 1.0)
            tiltScale = 1.0 + 99.0 * (clamped * clamped)
        }

        // Impact prediction
        if (tiltVelocity > 20.0) {
            tiltScale = kotlin.math.max(tiltScale, 50.0)
        }

        // Post-beaching recovery forces max scale
        if (inRecovery) {
            tiltScale = 100.0
        }

        // Online Gyro Bias Estimation & Bias Correction
        val isStationary = deltaTranslation.x == 0.0 && deltaTranslation.y == 0.0 && deltaHeading.radians == 0.0
        val alpha = 0.01 * dtSeconds
        val newBias = if (isStationary && gyroRateRadPerSec != 0.0) {
            state.gyroBiasRadPerSec * (1.0 - alpha) + gyroRateRadPerSec * alpha
        } else {
            state.gyroBiasRadPerSec
        }

        val correctedGyroRate = gyroRateRadPerSec - newBias
        val correctedDeltaHeading = if (isStationary) {
            0.0
        } else {
            deltaHeading.radians - newBias * dtSeconds
        }

        // Gyro rate mismatch check for wheel slippage detection
        val expectedHeadingVel = deltaHeading.radians / (if (dtSeconds > 1e-6) dtSeconds else 0.02)
        val slipScale = if (correctedGyroRate != 0.0 && kotlin.math.abs(expectedHeadingVel - correctedGyroRate) > 0.5) {
            10.0 // Dynamic wheel slippage covariance expansion
        } else {
            1.0
        }

        scratchQ.setTo(baseQ)
        val movementScale = if (isStationary) 0.001 else 1.0
        scratchQ.multiplyInPlace(tiltScale * slipScale * movementScale)

        val newHeading = Rotation2d(state.estimatedPoseHeading + correctedDeltaHeading)
        val newPose = Pose2d(
            state.estimatedPoseX + deltaTranslation.x,
            state.estimatedPoseY + deltaTranslation.y,
            newHeading
        )
        
        val thetaMid = state.estimatedPoseHeading + correctedDeltaHeading * 0.5
        val newCovariance = scratchCov

        EKFStatePropagator.propagate(
            state.covarianceArray,
            deltaTranslation.x,
            deltaTranslation.y,
            thetaMid,
            scratchQ,
            newCovariance
        )
        
        state.covarianceArray[0] = newCovariance.m00
        state.covarianceArray[1] = newCovariance.m01
        state.covarianceArray[2] = newCovariance.m02
        state.covarianceArray[3] = newCovariance.m10
        state.covarianceArray[4] = newCovariance.m11
        state.covarianceArray[5] = newCovariance.m12
        state.covarianceArray[6] = newCovariance.m20
        state.covarianceArray[7] = newCovariance.m21
        state.covarianceArray[8] = newCovariance.m22

        state.history.addEntry(timestampMs, newPose, newCovariance, tiltScale * slipScale)

        state.estimatedPoseX = newPose.x
        state.estimatedPoseY = newPose.y
        state.estimatedPoseHeading = newPose.heading.radians
        state.isBeached = currentlyBeached
        state.lastUnbeachedTimeMs = unbeachedTime
        state.gyroBiasRadPerSec = newBias

        return state
    }
}
