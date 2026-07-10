package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.DriveState
import com.areslib.state.DriveMode
import com.areslib.math.InputMath
import com.areslib.math.HistoryBuffer
import com.areslib.math.Matrix3x3
import com.areslib.math.Pose2d
import com.areslib.math.Translation2d
import com.areslib.math.Rotation2d
import com.areslib.math.PoseEstimator

object DriveReducer {
    /**
     * Reduces the DriveState slice independently based on drive actions.
     */
    fun reduce(state: DriveState, action: RobotAction): DriveState {
        return when (action) {
            is RobotAction.DriveHardwareUpdate -> {
                val deltaTrans = Translation2d(action.deltaX, action.deltaY)
                val deltaHeading = Rotation2d(action.deltaHeading)
                val updatedEstimator = PoseEstimator.addOdometryObservation(
                    state = state.poseEstimator,
                    timestampMs = action.timestampMs,
                    deltaTranslation = deltaTrans,
                    deltaHeading = deltaHeading,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees
                )
                val nextOdomX = state.odometryX + action.deltaX
                val nextOdomY = state.odometryY + action.deltaY
                val nextOdomHeading = state.odometryHeading + action.deltaHeading
                state.copy(
                    xVelocityMetersPerSecond = action.xVelocity,
                    yVelocityMetersPerSecond = action.yVelocity,
                    angularVelocityRadiansPerSecond = action.angularVelocity,
                    odometryX = nextOdomX,
                    odometryY = nextOdomY,
                    odometryHeading = nextOdomHeading,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees,
                    xAccelerationG = action.xAccelerationG,
                    yAccelerationG = action.yAccelerationG,
                    zAccelerationG = action.zAccelerationG
                ).updateDiagnostics(nextOdomX, nextOdomY, nextOdomHeading, updatedEstimator)
            }
            is RobotAction.PoseUpdate -> {
                val updatedEstimator = if (action.isReset) {
                    val newPose = Pose2d(action.xMeters, action.yMeters, Rotation2d(action.headingRadians))
                    val newHistory = HistoryBuffer(50)
                    newHistory.addEntry(action.timestampMs, newPose, Matrix3x3(0.01, 0.0, 0.0, 0.0, 0.01, 0.0, 0.0, 0.0, 0.01))
                    
                    state.poseEstimator.copy(
                        estimatedPose = newPose,
                        covariance = Matrix3x3(0.01, 0.0, 0.0, 0.0, 0.01, 0.0, 0.0, 0.0, 0.01),
                        history = newHistory,
                        isBeached = false,
                        lastUnbeachedTimeMs = action.timestampMs
                    )
                } else {
                    val deltaX = action.xMeters - state.odometryX
                    val deltaY = action.yMeters - state.odometryY
                    val deltaHeading = InputMath.wrapAngle(action.headingRadians - state.odometryHeading)

                    // Rotate the Pinpoint's world-frame delta into the EKF's world-frame
                    // by applying the rotational difference between their coordinate systems.
                    val headingDiff = state.poseEstimator.estimatedPose.heading.radians - state.odometryHeading
                    val cosH = kotlin.math.cos(headingDiff)
                    val sinH = kotlin.math.sin(headingDiff)
                    val rotatedDeltaX = deltaX * cosH - deltaY * sinH
                    val rotatedDeltaY = deltaX * sinH + deltaY * cosH

                    val deltaTrans = Translation2d(rotatedDeltaX, rotatedDeltaY)
                    PoseEstimator.addOdometryObservation(
                        state = state.poseEstimator,
                        timestampMs = action.timestampMs,
                        deltaTranslation = deltaTrans,
                        deltaHeading = Rotation2d(deltaHeading),
                        pitchDegrees = action.pitchDegrees,
                        rollDegrees = action.rollDegrees
                    )
                }

                state.copy(
                    odometryX = action.xMeters,
                    odometryY = action.yMeters,
                    odometryHeading = action.headingRadians,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees,
                    xAccelerationG = action.xAccelerationG,
                    yAccelerationG = action.yAccelerationG,
                    zAccelerationG = action.zAccelerationG
                ).updateDiagnostics(action.xMeters, action.yMeters, action.headingRadians, updatedEstimator)
            }
            is RobotAction.SetDriveMode -> {
                state.copy(driveMode = action.mode)
            }
            is RobotAction.SetAlliance -> {
                state.copy(alliance = action.alliance)
            }
            is RobotAction.SetHeadingLockTarget -> {
                state.copy(headingLockTargetRadians = action.targetRadians)
            }
            is RobotAction.JoystickDriveIntent -> {
                val hasLinearInput = kotlin.math.abs(action.targetXVelocity) > 0.05 || kotlin.math.abs(action.targetYVelocity) > 0.05
                val hasAngularInput = !action.fromHeadingHold && kotlin.math.abs(action.targetAngularVelocity) > 0.05
                
                val currentMode = state.driveMode
                val newMode = if (currentMode == DriveMode.X_BRAKE && (hasLinearInput || hasAngularInput)) {
                    DriveMode.TELEOP
                } else {
                    currentMode
                }

                val newTargetHeading = if (hasAngularInput) {
                    null
                } else {
                    state.headingLockTargetRadians
                }

                state.copy(
                    xVelocityMetersPerSecond = action.targetXVelocity,
                    yVelocityMetersPerSecond = action.targetYVelocity,
                    angularVelocityRadiansPerSecond = action.targetAngularVelocity,
                    driveMode = newMode,
                    headingLockTargetRadians = newTargetHeading,
                    isFieldCentric = action.isFieldCentric
                )
            }
            else -> state
        }
    }
}
