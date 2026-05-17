package com.areslib.math

import com.areslib.state.VisionMeasurement

data class PoseHistoryEntry(
    val timestampMs: Long = 0L,
    val pose: Pose2d = Pose2d(),
    val covariance: Matrix3x3 = Matrix3x3.IDENTITY
)

/**
 * Immutable chronological state representation of the Pose Estimator.
 * Avoids Android ART GC overhead by using small pre-sized lists and primitive-backed matrices.
 */
data class PoseEstimatorState(
    val estimatedPose: Pose2d = Pose2d(),
    val covariance: Matrix3x3 = Matrix3x3.IDENTITY,
    val history: List<PoseHistoryEntry> = emptyList() // Max size typically ~50
)

object PoseEstimator {
    private const val MAX_HISTORY_SIZE = 50

    // Standard deviation of odometry (tune these for actual robot)
    private val Q = Matrix3x3(
        0.01, 0.0,  0.0,
        0.0,  0.01, 0.0,
        0.0,  0.0,  0.01
    )

    /**
     * Integrates a new odometry delta into the state.
     * Call this every time wheel odometry updates.
     */
    fun addOdometryObservation(
        state: PoseEstimatorState,
        timestampMs: Long,
        deltaTranslation: Translation2d,
        deltaHeading: Rotation2d
    ): PoseEstimatorState {
        val newHeading = Rotation2d(state.estimatedPose.heading.radians + deltaHeading.radians)
        val newPose = Pose2d(
            state.estimatedPose.x + deltaTranslation.x,
            state.estimatedPose.y + deltaTranslation.y,
            newHeading
        )
        
        // Simple propagation: P = P + Q
        val newCovariance = state.covariance + Q

        val newEntry = PoseHistoryEntry(timestampMs, newPose, newCovariance)
        val newHistory = (state.history + newEntry).takeLast(MAX_HISTORY_SIZE)

        return state.copy(
            estimatedPose = newPose,
            covariance = newCovariance,
            history = newHistory
        )
    }

    /**
     * Fuses a vision measurement by retroactively calculating its effect 
     * at its exact timestamp, then re-integrating subsequent odometry.
     */
    fun addVisionMeasurement(
        state: PoseEstimatorState,
        measurement: VisionMeasurement,
        visionStdDevs: Vector3 // Vector3(x, y, heading)
    ): PoseEstimatorState {
        if (state.history.isEmpty()) return state

        // Find the index of the closest history entry before the vision measurement
        var closestIndex = -1
        for (i in state.history.indices.reversed()) {
            if (state.history[i].timestampMs <= measurement.timestampMs) {
                closestIndex = i
                break
            }
        }

        if (closestIndex == -1) {
            // Vision measurement is too old or we have no history, ignore it
            return state
        }

        val baseEntry = state.history[closestIndex]

        // Extended Kalman Filter Update at the historical timestamp
        val R = Vector3(
            visionStdDevs.x * visionStdDevs.x,
            visionStdDevs.y * visionStdDevs.y,
            visionStdDevs.z * visionStdDevs.z
        ).let { v -> 
            Matrix3x3(
                v.x, 0.0, 0.0,
                0.0, v.y, 0.0,
                0.0, 0.0, v.z
            )
        }

        // K = P * (P + R)^-1
        val S = baseEntry.covariance + R
        val S_inv = S.inverse()
        val K = baseEntry.covariance * S_inv

        // Residual y = z - Hx (where H is identity since we directly measure pose)
        val measurementPose2d = measurement.targetPose.toPose2d()
        var headingDiff = measurementPose2d.heading.radians - baseEntry.pose.heading.radians
        // normalize heading difference
        while (headingDiff > Math.PI) headingDiff -= 2 * Math.PI
        while (headingDiff < -Math.PI) headingDiff += 2 * Math.PI

        val y = Vector3(
            measurementPose2d.x - baseEntry.pose.x,
            measurementPose2d.y - baseEntry.pose.y,
            headingDiff
        )

        // Updated state delta: dx = K * y
        val dx = K * y

        // Updated covariance: P = (I - K) * P
        val I_minus_K = Matrix3x3.IDENTITY - K
        val updatedCovariance = I_minus_K * baseEntry.covariance

        val updatedPose = Pose2d(
            baseEntry.pose.x + dx.x,
            baseEntry.pose.y + dx.y,
            Rotation2d(baseEntry.pose.heading.radians + dx.z)
        )

        // Now we must replay all odometry deltas from closestIndex + 1 to the end
        var currentPose = updatedPose
        var currentCov = updatedCovariance
        val newHistory = state.history.toMutableList()
        
        newHistory[closestIndex] = PoseHistoryEntry(baseEntry.timestampMs, currentPose, currentCov)

        for (i in (closestIndex + 1) until state.history.size) {
            val prevRaw = state.history[i - 1].pose
            val currRaw = state.history[i].pose
            
            // Calculate raw delta
            val deltaX = currRaw.x - prevRaw.x
            val deltaY = currRaw.y - prevRaw.y
            val deltaHeading = currRaw.heading.radians - prevRaw.heading.radians

            // Re-apply delta to our updated state
            currentPose = Pose2d(
                currentPose.x + deltaX,
                currentPose.y + deltaY,
                Rotation2d(currentPose.heading.radians + deltaHeading)
            )
            currentCov = currentCov + Q
            
            newHistory[i] = PoseHistoryEntry(state.history[i].timestampMs, currentPose, currentCov)
        }

        return state.copy(
            estimatedPose = currentPose,
            covariance = currentCov,
            history = newHistory.toList()
        )
    }
}
