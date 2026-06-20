package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState

/**
 * A pure function that transitions the robot state based on the dispatched action.
 * Delegates domain state slices to specialized domain-focused sub-reducers to
 * prevent a single monolithic file and improve readability/extensibility.
 */
private val DEFAULT_STD_DEVS = com.areslib.math.Vector3(0.05, 0.05, 0.1)

fun rootReducer(state: RobotState, action: RobotAction): RobotState {
    return when (action) {
        is RobotAction.VisionMeasurementsReceived -> {
            val robotPose = state.drive.poseEstimator.estimatedPose
            val robotHeading = robotPose.heading.radians
            val measurements = action.measurements
            val validMeasurements = ArrayList<com.areslib.state.VisionMeasurement>(measurements.size)

            for (i in 0 until measurements.size) {
                val it = measurements[i]
                if (com.areslib.hardware.vision.VisionOutlierFilter.isValid(
                        config = state.vision.filterConfig,
                        measurement = it,
                        robotHeadingRad = robotHeading,
                        robotPose = robotPose,
                        angularVelocityRadPerSec = state.drive.angularVelocityRadiansPerSecond,
                        linearAccelXG = state.drive.xAccelerationG,
                        linearAccelYG = state.drive.yAccelerationG,
                        linearAccelZG = state.drive.zAccelerationG
                    )) {
                    validMeasurements.add(it)
                }
            }

            var currentEstimator = state.drive.poseEstimator
            val stdDevs = action.customVisionStdDevs ?: DEFAULT_STD_DEVS
            for (i in 0 until validMeasurements.size) {
                val measurement = validMeasurements[i]
                if (measurement.tagId == 1) continue // Skip Tag 1 for EKF fusion
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
