package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.state.VisionMeasurement
import com.areslib.math.Vector3
import com.areslib.math.PoseEstimator
import com.areslib.hardware.vision.VisionOutlierFilter

/**
 * A pure function that transitions the robot state based on the dispatched action.
 * Delegates domain state slices to specialized domain-focused sub-reducers to
 * prevent a single monolithic file and improve readability/extensibility.
 */
private val DEFAULT_STD_DEVS = Vector3(0.05, 0.05, 0.1)

fun rootReducer(state: RobotState, action: RobotAction): RobotState {
    return when (action) {
        is RobotAction.VisionMeasurementsReceived -> {
            val robotPose = state.drive.poseEstimator.estimatedPose
            val robotHeading = robotPose.heading.radians
            val measurements = action.measurements
            val validMeasurements = ArrayList<VisionMeasurement>(measurements.size)

            for (i in 0 until measurements.size) {
                val it = measurements[i]
                if (it.tagId == 1 || VisionOutlierFilter.isValid(
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
            val hasTag1 = run {
                var found = false
                for (j in 0 until validMeasurements.size) {
                    if (validMeasurements[j].tagId == 1) { found = true; break }
                }
                found
            }

            var acceptedCountDelta = 0
            var rejectedCountDelta = 0
            var lastCovBefore: List<Double>? = null
            var lastCovAfter: List<Double>? = null
            var lastAccepted = false
            var lastReason: String? = null

            if (!hasTag1) {
                for (i in 0 until validMeasurements.size) {
                    val measurement = validMeasurements[i]
                    val covBefore = listOf(
                        currentEstimator.covariance.m00, currentEstimator.covariance.m01, currentEstimator.covariance.m02,
                        currentEstimator.covariance.m10, currentEstimator.covariance.m11, currentEstimator.covariance.m12,
                        currentEstimator.covariance.m20, currentEstimator.covariance.m21, currentEstimator.covariance.m22
                    )
                    currentEstimator = PoseEstimator.addVisionMeasurement(
                        state = currentEstimator,
                        measurement = measurement,
                        visionStdDevs = stdDevs,
                        numTags = validMeasurements.size
                    )
                    lastAccepted = currentEstimator.lastMeasurementAccepted
                    lastReason = currentEstimator.lastRejectionReason
                    if (lastAccepted) {
                        acceptedCountDelta++
                        lastCovBefore = covBefore
                        lastCovAfter = listOf(
                            currentEstimator.covariance.m00, currentEstimator.covariance.m01, currentEstimator.covariance.m02,
                            currentEstimator.covariance.m10, currentEstimator.covariance.m11, currentEstimator.covariance.m12,
                            currentEstimator.covariance.m20, currentEstimator.covariance.m21, currentEstimator.covariance.m22
                        )
                    } else {
                        rejectedCountDelta++
                    }
                }
            }

            val filteredAction = action.copy(measurements = validMeasurements)
            val reducedVision = VisionReducer.reduce(state.vision, filteredAction)
            val updatedVision = reducedVision.copy(
                lastMeasurementAccepted = lastAccepted,
                lastRejectionReason = lastReason,
                covarianceBeforeUpdate = lastCovBefore ?: reducedVision.covarianceBeforeUpdate,
                covarianceAfterUpdate = lastCovAfter ?: reducedVision.covarianceAfterUpdate,
                measurementCount = reducedVision.measurementCount + acceptedCountDelta,
                rejectionCount = reducedVision.rejectionCount + rejectedCountDelta
            )

            val updatedDrive = state.drive.updateDiagnostics(
                state.drive.odometryX,
                state.drive.odometryY,
                state.drive.odometryHeading,
                currentEstimator
            )

            state.copy(
                vision = updatedVision,
                drive = updatedDrive,
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
