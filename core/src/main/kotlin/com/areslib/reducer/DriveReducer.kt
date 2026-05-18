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
                state.copy(
                    odometryX = action.xMeters,
                    odometryY = action.yMeters,
                    odometryHeading = action.headingRadians,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees,
                    xAccelerationG = action.xAccelerationG,
                    yAccelerationG = action.yAccelerationG,
                    zAccelerationG = action.zAccelerationG
                )
            }
            else -> state
        }
    }
}
