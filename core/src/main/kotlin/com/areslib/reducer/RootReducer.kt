package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState

/**
 * A pure function that transitions the robot state based on the dispatched action.
 */
fun rootReducer(state: RobotState, action: RobotAction): RobotState {
    return when (action) {
        is RobotAction.DriveHardwareUpdate -> {
            state.copy(
                drive = state.drive.copy(
                    xVelocityMetersPerSecond = action.xVelocity,
                    yVelocityMetersPerSecond = action.yVelocity,
                    angularVelocityRadiansPerSecond = action.angularVelocity,
                    odometryX = state.drive.odometryX + action.deltaX,
                    odometryY = state.drive.odometryY + action.deltaY,
                    odometryHeading = state.drive.odometryHeading + action.deltaHeading
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.VisionUpdate -> {
            state.copy(
                vision = state.vision.copy(
                    hasTarget = action.hasTarget,
                    targetX = action.targetX,
                    targetY = action.targetY,
                    lastTargetTimestampMs = action.timestampMs
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.JoystickDriveIntent -> {
            // Intent doesn't immediately change the physical state, but in a more 
            // advanced system we might store target state separately. For now, we 
            // simply acknowledge we can reduce intent.
            state
        }
        is RobotAction.PoseUpdate -> {
            state.copy(
                drive = state.drive.copy(
                    odometryX = action.xMeters,
                    odometryY = action.yMeters,
                    odometryHeading = action.headingRadians
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.PathEventTriggered -> {
            if (action.eventName == "IntakeOn") {
                state.copy(
                    superstructure = state.superstructure.copy(intakeActive = true),
                    timestampMs = action.timestampMs
                )
            } else if (action.eventName == "IntakeOff") {
                state.copy(
                    superstructure = state.superstructure.copy(intakeActive = false),
                    timestampMs = action.timestampMs
                )
            } else {
                state
            }
        }
    }
}
