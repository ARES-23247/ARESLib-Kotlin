package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState

/**
 * A pure function that transitions the robot state based on the dispatched action.
 * Delegates domain state slices to specialized domain-focused sub-reducers to
 * prevent a single monolithic file and improve readability/extensibility.
 */
fun rootReducer(state: RobotState, action: RobotAction): RobotState {
    return when (action) {
        is RobotAction.VisionMeasurementsReceived -> {
            val robotPose = state.drive.poseEstimator.estimatedPose
            val robotHeading = robotPose.heading.radians
            val validMeasurements = action.measurements.filter {
                val visionFilter = com.areslib.hardware.vision.VisionOutlierFilter(state.vision.filterConfig)
                visionFilter.isValid(
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
            val stdDevs = action.customVisionStdDevs ?: com.areslib.math.Vector3(0.05, 0.05, 0.1)
            for (measurement in validMeasurements) {
                currentEstimator = com.areslib.math.PoseEstimator.addVisionMeasurement(
                    state = currentEstimator,
                    measurement = measurement,
                    visionStdDevs = stdDevs,
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
        else -> {
            // Standard action propagation: Compose all independent domain slice reducers
            val robotPose = state.drive.poseEstimator.estimatedPose
            state.copy(
                drive = DriveReducer.reduce(state.drive, action),
                vision = VisionReducer.reduce(state.vision, action),
                superstructure = SuperstructureReducer.reduce(state.superstructure, action),
                pathState = PathReducer.reduce(state.pathState, action),
                costmap = CostmapReducer.reduce(state.costmap, action, robotPose),
                timestampMs = action.timestampMs
            )
        }
    }
}
