package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import com.areslib.state.CostmapState
import com.areslib.state.Obstacle

/**
 * A pure function that transitions the robot state based on the dispatched action.
 */
fun rootReducer(state: RobotState, action: RobotAction): RobotState {
    return when (action) {
        is RobotAction.DriveHardwareUpdate -> {
            val deltaTrans = com.areslib.math.Translation2d(action.deltaX, action.deltaY)
            val deltaHeading = com.areslib.math.Rotation2d(action.deltaHeading)
            val updatedEstimator = com.areslib.math.PoseEstimator.addOdometryObservation(
                state = state.drive.poseEstimator,
                timestampMs = action.timestampMs,
                deltaTranslation = deltaTrans,
                deltaHeading = deltaHeading,
                pitchDegrees = action.pitchDegrees,
                rollDegrees = action.rollDegrees
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
                filter.isValid(
                    measurement = it,
                    robotHeadingRad = robotHeading,
                    robotPose = robotPose,
                    angularVelocityRadPerSec = state.drive.angularVelocityRadiansPerSecond,
                    linearAccelXG = state.drive.xAccelerationG,
                    linearAccelYG = state.drive.yAccelerationG,
                    linearAccelZG = state.drive.zAccelerationG
                )
            }

            var currentEstimator = state.drive.poseEstimator
            for (measurement in validMeasurements) {
                currentEstimator = com.areslib.math.PoseEstimator.addVisionMeasurement(
                    state = currentEstimator,
                    measurement = measurement,
                    visionStdDevs = com.areslib.math.Vector3(0.05, 0.05, 0.1), // Vision std dev tuning
                    numTags = validMeasurements.size
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
        is RobotAction.ObstacleCostmapUpdate -> {
            val robotPose = state.drive.poseEstimator.estimatedPose
            val robotX = robotPose.x
            val robotY = robotPose.y
            val robotHeadingRad = robotPose.heading.radians

            val currentObstacles = state.costmap.obstacles.toMutableList()

            for (obs in action.observations) {
                val sensorFieldAngle = robotHeadingRad + obs.angleOffsetRad
                
                val mountFieldX = obs.positionOffsetXMeters * kotlin.math.cos(robotHeadingRad) - 
                                  obs.positionOffsetYMeters * kotlin.math.sin(robotHeadingRad)
                val mountFieldY = obs.positionOffsetXMeters * kotlin.math.sin(robotHeadingRad) + 
                                  obs.positionOffsetYMeters * kotlin.math.cos(robotHeadingRad)
                                  
                val sensorFieldX = robotX + mountFieldX
                val sensorFieldY = robotY + mountFieldY

                if (obs.distanceMeters < obs.maxRangeMeters) {
                    val obstacleFieldX = sensorFieldX + obs.distanceMeters * kotlin.math.cos(sensorFieldAngle)
                    val obstacleFieldY = sensorFieldY + obs.distanceMeters * kotlin.math.sin(sensorFieldAngle)

                    val mergeThreshold = 0.3
                    val existingIdx = currentObstacles.indexOfFirst { 
                        kotlin.math.hypot(it.x - obstacleFieldX, it.y - obstacleFieldY) < mergeThreshold 
                    }

                    val newObstacle = Obstacle(obstacleFieldX, obstacleFieldY)
                    if (existingIdx >= 0) {
                        currentObstacles[existingIdx] = newObstacle
                    } else {
                        currentObstacles.add(newObstacle)
                    }
                } else {
                    currentObstacles.removeAll { obstacle ->
                        val dx = obstacle.x - sensorFieldX
                        val dy = obstacle.y - sensorFieldY
                        val dist = kotlin.math.hypot(dx, dy)
                        val angleToObstacle = kotlin.math.atan2(dy, dx)
                        
                        var angleDiff = angleToObstacle - sensorFieldAngle
                        while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI
                        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI
                        
                        dist < obs.maxRangeMeters && kotlin.math.abs(angleDiff) < Math.toRadians(15.0)
                    }
                }
            }

            if (currentObstacles.size > 50) {
                while (currentObstacles.size > 50) {
                    currentObstacles.removeAt(0)
                }
            }

            state.copy(
                costmap = state.costmap.copy(
                    obstacles = currentObstacles,
                    lastUpdateTimestampMs = action.timestampMs
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.ChainPaths -> {
            val chained = com.areslib.pathing.PathChainer.chainPaths(
                action.paths,
                action.maxVelocityMps,
                action.maxAccelerationMps2
            )
            state.copy(
                pathState = state.pathState.copy(
                    activePath = chained,
                    currentDistanceMeters = 0.0,
                    isChained = true,
                    detourActive = false
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.SwitchPath -> {
            val backupPath = if (action.isDetour) {
                state.pathState.originalPathBeforeDetour ?: state.pathState.activePath
            } else {
                null
            }
            state.copy(
                pathState = state.pathState.copy(
                    activePath = action.path,
                    currentDistanceMeters = 0.0,
                    detourActive = action.isDetour,
                    originalPathBeforeDetour = backupPath
                ),
                timestampMs = action.timestampMs
            )
        }
        is RobotAction.UpdatePathProgress -> {
            state.copy(
                pathState = state.pathState.copy(
                    currentDistanceMeters = action.distanceProgressMeters
                ),
                timestampMs = action.timestampMs
            )
        }
    }
}
