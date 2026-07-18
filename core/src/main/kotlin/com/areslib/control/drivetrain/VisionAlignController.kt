package com.areslib.control.drivetrain

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.math.wrapAngle
import com.areslib.util.RobotClock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * Closed-loop controller for aligning to an AprilTag (typically the scoring target).
 * Handles low-pass filtering of vision noise, deadbands, translation/heading P-D control, 
 * and search behaviors when the tag is temporarily lost.
 */
class VisionAlignController {
    private var hasPrevFiltered = false
    private var prevRawYaw = 0.0
    private var prevErrX = 0.0
    private var prevErrY = 0.0
    private var prevErrHeading = 0.0
    private var prevErrHeadingForD = 0.0
    private var prevLoopTimeMs = RobotClock.currentTimeMillis()

    // Tag search state
    private var lastKnownSearchDirection = 0.0 // +1.0 = rotate CCW, -1.0 = rotate CW
    private var tagLostTimestampMs = 0L
    private var wasTrackingTag = false

    /**
     * Calculates the required drive intent to align the robot to the specified AprilTag.
     * 
     * @param state The current immutable Redux RobotState (for vision measurements).
     * @param targetTagId The ID of the AprilTag to align with.
     * @param isAlignmentRequested Whether the driver is currently holding the align button.
     * @return A JoystickDriveIntent (or null if not requested).
     */
    fun calculate(state: RobotState, targetTagId: Int, isAlignmentRequested: Boolean): RobotAction.JoystickDriveIntent? {
        if (!isAlignmentRequested) {
            // Reset state when button is released
            wasTrackingTag = false
            tagLostTimestampMs = 0L
            hasPrevFiltered = false
            return null
        }

        val now = RobotClock.currentTimeMillis()
        
        // Require reasonably fresh data (<= 250ms) for active closed-loop control
        var activeMeasurement: com.areslib.state.VisionMeasurement? = null
        for (i in 0 until state.vision.measurements.size) {
            val measurement = state.vision.measurements[i]
            if (measurement.tagId == targetTagId && (now - measurement.timestampMs) < 250L) {
                activeMeasurement = measurement
                break
            }
        }

        if (activeMeasurement != null) {
            // Tag reacquired — reset search state
            tagLostTimestampMs = 0L
            
            val robotPoseTargetSpace = activeMeasurement.robotPoseTargetSpace
            
            // target-space coordinates (Limelight: Z forward, X right)
            val tuning = state.tuning
            val distanceZ = abs(robotPoseTargetSpace.z)
            val targetDistanceMeters = tuning.visionAlignTargetDistance
            val errorForwardT = distanceZ - targetDistanceMeters
            val errorLeftT = robotPoseTargetSpace.x
            
            // In target-space: Z+ is outward from tag, Y+ is up.
            // Yaw (robot turning left/right) = rotation around Y axis = rotation.y
            // Negated to match the controller's sign convention (positive = CCW)
            val robotYaw = -robotPoseTargetSpace.rotation.y
            val wrappedYaw = wrapAngle(robotYaw)
            
            // 1. Yaw rate-of-change sanity check (reject PnP flips/jumps)
            val maxHeadingChange = tuning.visionAlignMaxHeadingChangeRad
            val sanitizedYaw = if (hasPrevFiltered) {
                val diff = wrapAngle(wrappedYaw - prevRawYaw)
                if (abs(diff) > maxHeadingChange) prevRawYaw else wrappedYaw
            } else {
                wrappedYaw
            }
            prevRawYaw = sanitizedYaw
            
            val phi = sanitizedYaw
            // Rotate translation errors into robot-centric frame using the correct -phi rotation matrix
            val errX = errorForwardT * cos(phi) + errorLeftT * sin(phi)
            val errY = -errorForwardT * sin(phi) + errorLeftT * cos(phi)
            
            // Heading goal: rotate to keep the tag centered in the camera FOV
            val pointingTarget = atan2(errorLeftT, distanceZ)
            val errHeading = wrapAngle(pointingTarget - phi)
            
            // 2. Low-pass filters to smooth out high-frequency vision noise
            val alphaTranslation = tuning.visionAlignAlphaTranslation
            val alphaHeading = tuning.visionAlignAlphaHeading
            
            val errXFiltered = if (hasPrevFiltered) alphaTranslation * errX + (1.0 - alphaTranslation) * prevErrX else errX
            val errYFiltered = if (hasPrevFiltered) alphaTranslation * errY + (1.0 - alphaTranslation) * prevErrY else errY
            
            val errHeadingFiltered = if (hasPrevFiltered) {
                val diff = wrapAngle(errHeading - prevErrHeading)
                wrapAngle(prevErrHeading + alphaHeading * diff)
            } else {
                errHeading
            }
            
            prevErrX = errXFiltered
            prevErrY = errYFiltered
            prevErrHeading = errHeadingFiltered
            hasPrevFiltered = true
            
            val kP_translation = tuning.visionAlignKpTranslation
            val kP_rotation = tuning.visionAlignKpRotation
            val kD_rotation = tuning.visionAlignKdRotation
            
            // 3. Apply deadbands to prevent limit-cycle oscillations (jittering)
            val translationDeadband = tuning.visionAlignTranslationDeadband
            val headingErrorDeadband = tuning.visionAlignHeadingErrorDeadband
            
            // Speed-limit translation commands to keep the tag in the camera's FOV
            val ctrlX = if (abs(errXFiltered) > translationDeadband) {
                (errXFiltered * kP_translation).coerceIn(-tuning.visionAlignClampTranslationX, tuning.visionAlignClampTranslationX)
            } else 0.0
            
            val ctrlY = if (abs(errYFiltered) > translationDeadband) {
                (errYFiltered * kP_translation).coerceIn(-tuning.visionAlignClampTranslationY, tuning.visionAlignClampTranslationY)
            } else 0.0
            
            val kS_rotational = tuning.visionAlignKsRotational
            
            // Compute derivative term: rate of heading error change
            val dtSec = ((now - prevLoopTimeMs).coerceIn(1, 200)) / 1000.0
            prevLoopTimeMs = now
            val headingErrorRate = if (hasPrevFiltered) {
                wrapAngle(errHeadingFiltered - prevErrHeadingForD) / dtSec
            } else 0.0
            prevErrHeadingForD = errHeadingFiltered
            
            val ctrlOmega = if (abs(errHeadingFiltered) > headingErrorDeadband) {
                val currentSign = sign(errHeadingFiltered)
                val pTerm = errHeadingFiltered * kP_rotation
                val dTerm = headingErrorRate * kD_rotation
                (pTerm + dTerm + currentSign * kS_rotational).coerceIn(-tuning.visionAlignClampRotation, tuning.visionAlignClampRotation)
            } else 0.0

            // Update search direction
            when {
                abs(ctrlOmega) > 0.02 -> lastKnownSearchDirection = sign(ctrlOmega)
                abs(ctrlX) > 0.02 -> lastKnownSearchDirection = -sign(ctrlX)
            }
            wasTrackingTag = true
            
            return RobotAction.JoystickDriveIntent(
                targetXVelocity = ctrlX,
                targetYVelocity = ctrlY,
                targetAngularVelocity = ctrlOmega,
                isFieldCentric = false
            )
        } else {
            val tuning = state.tuning
            hasPrevFiltered = false
            
            // Tag is not visible while requested — initiate search rotation
            if (tagLostTimestampMs == 0L) {
                tagLostTimestampMs = RobotClock.currentTimeMillis()
                if (!wasTrackingTag) lastKnownSearchDirection = -1.0 // start CW
            }
            
            val firstSweepMs = tuning.visionAlignSearchFirstSweepMs
            val secondSweepMs = tuning.visionAlignSearchSecondSweepMs
            val totalSearchMs = firstSweepMs + secondSweepMs
            val timeSinceLost = RobotClock.currentTimeMillis() - tagLostTimestampMs
            val searchSpeed = tuning.visionAlignSearchSpeed
            
            if (timeSinceLost < totalSearchMs) {
                // Active search
                val currentDirection = if (timeSinceLost < firstSweepMs) lastKnownSearchDirection else -lastKnownSearchDirection
                val searchOmega = currentDirection * searchSpeed
                return RobotAction.JoystickDriveIntent(
                    targetXVelocity = 0.0,
                    targetYVelocity = 0.0,
                    targetAngularVelocity = searchOmega,
                    isFieldCentric = false
                )
            } else {
                // Both sweeps exhausted — stop
                return RobotAction.JoystickDriveIntent(
                    targetXVelocity = 0.0,
                    targetYVelocity = 0.0,
                    targetAngularVelocity = 0.0,
                    isFieldCentric = false
                )
            }
        }
    }
}
