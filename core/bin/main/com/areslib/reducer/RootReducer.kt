package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState

/**
 * A pure function that transitions the robot state based on the dispatched action.
 */
fun rootReducer(state: RobotState, action: RobotAction): RobotState {
    return when (action) {
        is RobotAction.DriveHardwareUpdate -> {
            val deltaTrans = com.areslib.math.Translation2d(action.deltaX, action.deltaY)
            val deltaHeading = com.areslib.math.Rotation2d(action.deltaHeading)
            val updatedEstimator = com.areslib.math.PoseEstimator.addOdometryObservation(
                state.drive.poseEstimator,
                action.timestampMs,
                deltaTrans,
                deltaHeading
            )
            state.copy(
                drive = state.drive.copy(
                    xVelocityMetersPerSecond = action.xVelocity,
                    yVelocityMetersPerSecond = action.yVelocity,
                    angularVelocityRadiansPerSecond = action.angularVelocity,
                    odometryX = state.drive.odometryX + action.deltaX,
                    odometryY = state.drive.odometryY + action.deltaY,
                    odometryHeading = state.drive.odometryHeading + action.deltaHeading,
                    poseEstimator = updatedEstimator
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
        is RobotAction.VisionMeasurementsReceived -> {
            var currentEstimator = state.drive.poseEstimator
            val validMeasurements = action.measurements.filter { it.ambiguity < 0.2 }
            for (measurement in validMeasurements) {
                currentEstimator = com.areslib.math.PoseEstimator.addVisionMeasurement(
                    currentEstimator,
                    measurement,
                    com.areslib.math.Vector3(0.05, 0.05, 0.1) // Vision std dev tuning
                )
            }
            state.copy(
                vision = VisionReducer.reduce(state.vision, action),
                drive = state.drive.copy(poseEstimator = currentEstimator),
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
        is RobotAction.SetIntakeActive -> {
            state.copy(
                superstructure = state.superstructure.copy(intakeActive = action.active),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.SetInventoryCount -> {
            state.copy(
                superstructure = state.superstructure.copy(inventoryCount = action.count),
                timestampMs = action.timestampMs
            )
        }
    }
}
