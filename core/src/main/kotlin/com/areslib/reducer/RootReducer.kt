package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode

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
                    poseEstimator = updatedEstimator,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees,
                    xAccelerationG = action.xAccelerationG,
                    yAccelerationG = action.yAccelerationG,
                    zAccelerationG = action.zAccelerationG
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
            val filter = com.areslib.hardware.vision.VisionOutlierFilter()
            val robotPose = state.drive.poseEstimator.estimatedPose
            val robotHeading = robotPose.heading.radians
            val validMeasurements = action.measurements.filter {
                filter.isValid(it, robotHeading, robotPose)
            }

            var currentEstimator = state.drive.poseEstimator
            for (measurement in validMeasurements) {
                currentEstimator = com.areslib.math.PoseEstimator.addVisionMeasurement(
                    currentEstimator,
                    measurement,
                    com.areslib.math.Vector3(0.05, 0.05, 0.1) // Vision std dev tuning
                )
            }

            val filteredAction = action.copy(measurements = validMeasurements)
            state.copy(
                vision = VisionReducer.reduce(state.vision, filteredAction),
                drive = state.drive.copy(poseEstimator = currentEstimator),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.JoystickDriveIntent -> {
            state
        }
        is RobotAction.PoseUpdate -> {
            state.copy(
                drive = state.drive.copy(
                    odometryX = action.xMeters,
                    odometryY = action.yMeters,
                    odometryHeading = action.headingRadians,
                    pitchDegrees = action.pitchDegrees,
                    rollDegrees = action.rollDegrees,
                    xAccelerationG = action.xAccelerationG,
                    yAccelerationG = action.yAccelerationG,
                    zAccelerationG = action.zAccelerationG
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.PathEventTriggered -> {
            if (action.eventName == "IntakeOn") {
                state.copy(
                    superstructure = state.superstructure.copy(
                        intakeActive = true,
                        mode = SuperstructureMode.INTAKING
                    ),
                    timestampMs = action.timestampMs
                )
            } else if (action.eventName == "IntakeOff") {
                state.copy(
                    superstructure = state.superstructure.copy(
                        intakeActive = false,
                        mode = if (state.superstructure.flywheelActive) {
                            if (state.superstructure.isFlywheelAtSpeed) SuperstructureMode.FLYWHEEL_READY
                            else SuperstructureMode.FLYWHEEL_SPINUP
                        } else SuperstructureMode.IDLE
                    ),
                    timestampMs = action.timestampMs
                )
            } else {
                state
            }
        }
        is RobotAction.SetIntakeActive -> {
            val newMode = if (action.active) {
                SuperstructureMode.INTAKING
            } else if (state.superstructure.flywheelActive) {
                if (state.superstructure.isFlywheelAtSpeed) SuperstructureMode.FLYWHEEL_READY
                else SuperstructureMode.FLYWHEEL_SPINUP
            } else {
                SuperstructureMode.IDLE
            }
            state.copy(
                superstructure = state.superstructure.copy(
                    intakeActive = action.active,
                    mode = newMode
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.SetFlywheelActive -> {
            val newMode = if (action.active) {
                SuperstructureMode.FLYWHEEL_SPINUP
            } else {
                // Turning off flywheel — also stop transfer
                if (state.superstructure.intakeActive) SuperstructureMode.INTAKING
                else SuperstructureMode.IDLE
            }
            state.copy(
                superstructure = state.superstructure.copy(
                    flywheelActive = action.active,
                    transferActive = if (!action.active) false else state.superstructure.transferActive,
                    mode = newMode
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.SetTransferActive -> {
            // Transfer only activates when flywheel is at speed
            val canTransfer = action.active && state.superstructure.isFlywheelAtSpeed && state.superstructure.inventoryCount > 0
            val newMode = if (canTransfer) {
                SuperstructureMode.SHOOTING
            } else if (state.superstructure.flywheelActive) {
                if (state.superstructure.isFlywheelAtSpeed) SuperstructureMode.FLYWHEEL_READY
                else SuperstructureMode.FLYWHEEL_SPINUP
            } else if (state.superstructure.intakeActive) {
                SuperstructureMode.INTAKING
            } else {
                SuperstructureMode.IDLE
            }
            state.copy(
                superstructure = state.superstructure.copy(
                    transferActive = canTransfer,
                    mode = newMode
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.UpdateFlywheelRPM -> {
            // Derive the mode from current RPM and flywheel state
            val newMode = when {
                state.superstructure.transferActive && state.superstructure.isFlywheelAtSpeed -> SuperstructureMode.SHOOTING
                state.superstructure.flywheelActive && action.rpm >= state.superstructure.flywheelTargetRPM * 0.95 -> SuperstructureMode.FLYWHEEL_READY
                state.superstructure.flywheelActive -> SuperstructureMode.FLYWHEEL_SPINUP
                state.superstructure.intakeActive -> SuperstructureMode.INTAKING
                else -> SuperstructureMode.IDLE
            }
            state.copy(
                superstructure = state.superstructure.copy(
                    flywheelRPM = action.rpm,
                    mode = newMode
                ),
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
