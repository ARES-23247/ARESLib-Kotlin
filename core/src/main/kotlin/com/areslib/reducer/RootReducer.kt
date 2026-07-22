package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.state.VisionMeasurement
import com.areslib.math.geometry.Vector3
import com.areslib.math.estimation.PoseEstimator
import com.areslib.hardware.vision.VisionOutlierFilter

/**
 * A pure function that transitions the robot state based on the dispatched action.
 * Delegates domain state slices to specialized domain-focused sub-reducers to
 * prevent a single monolithic file and improve readability/extensibility.
 */
private val DEFAULT_STD_DEVS = Vector3(0.05, 0.05, 0.1)

private val rootReducerScratchBefore = object : ThreadLocal<DoubleArray>() {
    /**
     * initialValue declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun initialValue() = DoubleArray(9)
}

private val rootReducerScratchAfter = object : ThreadLocal<DoubleArray>() {
    /**
     * initialValue declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun initialValue() = DoubleArray(9)
}

/**
 * rootReducer declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
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
            var acceptedCountDelta = 0
            var rejectedCountDelta = 0
            var lastCovBefore: DoubleArray? = null
            var lastCovAfter: DoubleArray? = null
            var lastAccepted = false
            var lastReason: String? = null
            
            // Scratchpad arrays for avoiding allocations
            val scratchBefore = rootReducerScratchBefore.get()!!
            val scratchAfter = rootReducerScratchAfter.get()!!

            for (i in 0 until validMeasurements.size) {
                val measurement = validMeasurements[i]
                scratchBefore[0] = currentEstimator.covariance.m00
                scratchBefore[1] = currentEstimator.covariance.m01
                scratchBefore[2] = currentEstimator.covariance.m02
                scratchBefore[3] = currentEstimator.covariance.m10
                scratchBefore[4] = currentEstimator.covariance.m11
                scratchBefore[5] = currentEstimator.covariance.m12
                scratchBefore[6] = currentEstimator.covariance.m20
                scratchBefore[7] = currentEstimator.covariance.m21
                scratchBefore[8] = currentEstimator.covariance.m22

                currentEstimator = PoseEstimator.addVisionMeasurement(
                    state = currentEstimator,
                    measurement = measurement,
                    visionStdDevs = stdDevs,
                    numTags = validMeasurements.size,
                    useMahalanobisRejection = true,
                    mahalanobisThreshold = state.vision.filterConfig.mahalanobisThreshold
                )
                lastAccepted = currentEstimator.lastMeasurementAccepted
                lastReason = currentEstimator.lastRejectionReason
                if (lastAccepted) {
                    acceptedCountDelta++
                    if (lastCovBefore == null) {
                        lastCovBefore = state.vision.covarianceBeforeUpdate ?: DoubleArray(9)
                    }
                    if (lastCovAfter == null) {
                        lastCovAfter = state.vision.covarianceAfterUpdate ?: DoubleArray(9)
                    }
                    
                    System.arraycopy(scratchBefore, 0, lastCovBefore, 0, 9)
                    
                    scratchAfter[0] = currentEstimator.covariance.m00
                    scratchAfter[1] = currentEstimator.covariance.m01
                    scratchAfter[2] = currentEstimator.covariance.m02
                    scratchAfter[3] = currentEstimator.covariance.m10
                    scratchAfter[4] = currentEstimator.covariance.m11
                    scratchAfter[5] = currentEstimator.covariance.m12
                    scratchAfter[6] = currentEstimator.covariance.m20
                    scratchAfter[7] = currentEstimator.covariance.m21
                    scratchAfter[8] = currentEstimator.covariance.m22
                    
                    System.arraycopy(scratchAfter, 0, lastCovAfter, 0, 9)
                } else {
                    rejectedCountDelta++
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
        is RobotAction.UpdateTuningState -> {
            val filterConfig = state.vision.filterConfig.copy(
                maxDistanceMeters = action.tuning.visionMaxDistanceMeters,
                maxAmbiguity = action.tuning.visionMaxAmbiguity,
                mahalanobisThreshold = action.tuning.visionMahalanobisThreshold
            )
            val updatedVision = state.vision.copy(filterConfig = filterConfig)
            
            state.copy(
                tuning = action.tuning,
                vision = updatedVision,
                timestampMs = action.timestampMs
            )
        }
        else -> {
            // Standard action propagation: Compose all independent domain slice reducers
            state.copy(
                drive = DriveReducer.reduce(state.drive, action),
                vision = VisionReducer.reduce(state.vision, action),
                superstructure = SuperstructureReducer.reduce(state.superstructure, action),
                pathState = PathReducer.reduce(state.pathState, action),
                timestampMs = action.timestampMs
            )
        }
    }
}
