package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.DriveState
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
                state.copy(
                    xVelocityMetersPerSecond = action.xVelocity,
                    yVelocityMetersPerSecond = action.yVelocity,
                    angularVelocityRadiansPerSecond = action.angularVelocity,
                    odometryX = state.odometryX + action.deltaX,
                    odometryY = state.odometryY + action.deltaY,
                    odometryHeading = state.odometryHeading + action.deltaHeading,
                    poseEstimator = updatedEstimator,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees,
                    xAccelerationG = action.xAccelerationG,
                    yAccelerationG = action.yAccelerationG,
                    zAccelerationG = action.zAccelerationG
                )
            }
            is RobotAction.PoseUpdate -> {
                val deltaX = action.xMeters - state.odometryX
                val deltaY = action.yMeters - state.odometryY
                val deltaHeading = com.areslib.math.InputMath.wrapAngle(action.headingRadians - state.odometryHeading)

                val deltaTrans = Translation2d(deltaX, deltaY)
                val updatedEstimator = PoseEstimator.addOdometryObservation(
                    state = state.poseEstimator,
                    timestampMs = action.timestampMs,
                    deltaTranslation = deltaTrans,
                    deltaHeading = Rotation2d(deltaHeading),
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees
                )

                state.copy(
                    odometryX = action.xMeters,
                    odometryY = action.yMeters,
                    odometryHeading = action.headingRadians,
                    poseEstimator = updatedEstimator,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees,
                    xAccelerationG = action.xAccelerationG,
                    yAccelerationG = action.yAccelerationG,
                    zAccelerationG = action.zAccelerationG
                )
            }
            is RobotAction.SetDriveMode -> {
                state.copy(driveMode = action.mode)
            }
            is RobotAction.SetHeadingLockTarget -> {
                state.copy(headingLockTargetRadians = action.targetRadians)
            }
            is RobotAction.JoystickDriveIntent -> {
                val hasLinearInput = kotlin.math.abs(action.targetXVelocity) > 0.05 || kotlin.math.abs(action.targetYVelocity) > 0.05
                val hasAngularInput = kotlin.math.abs(action.targetAngularVelocity) > 0.05
                
                val currentMode = state.driveMode
                val newMode = if (currentMode == com.areslib.state.DriveMode.X_BRAKE && (hasLinearInput || hasAngularInput)) {
                    com.areslib.state.DriveMode.TELEOP
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
                    headingLockTargetRadians = newTargetHeading
                )
            }
            else -> state
        }
    }
}
