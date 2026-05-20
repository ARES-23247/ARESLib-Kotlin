package com.areslib.math

import com.areslib.state.VisionMeasurement

data class PoseHistoryEntry(
    val timestampMs: Long = 0L,
    val pose: Pose2d = Pose2d(),
    val covariance: Matrix3x3 = Matrix3x3.IDENTITY
)

/**
 * Immutable chronological state representation of the Pose Estimator.
 *
 * Designed to prevent high-frequency garbage collection overhead in Android ART
 * and RoboRIO runtimes by utilizing small, pre-allocated lists and primitive-backed matrices.
 *
 * @property estimatedPose The current best estimate of the robot's 2D field-centric position and heading.
 * @property covariance The 3x3 error covariance matrix representing estimate uncertainty.
 * @property history The rolling history of past state estimations used for retroactive latency compensation.
 */
data class PoseEstimatorState(
    val estimatedPose: Pose2d = Pose2d(),
    val covariance: Matrix3x3 = Matrix3x3.IDENTITY,
    val history: List<PoseHistoryEntry> = emptyList(), // Max size typically ~50
    val isBeached: Boolean = false,
    val lastUnbeachedTimeMs: Long = 0L
)

/**
 * A world-class, high-fidelity Extended Kalman Filter (EKF) state estimator.
 *
 * This estimator fuses high-frequency wheel odometry with asynchronous, latency-delayed 3D vision measurements
 * (such as multi-tag Perspective-n-Point observations). It incorporates:
 * - Dynamic gyro-rate slip covariance scaling to adapt to wheel slippage.
 * - Retroactive observation rewinding to compensate for vision processing latency.
 * - Statistical 3-DOF Mahalanobis Distance outlier rejection to handle erroneous camera targets.
 */
object PoseEstimator {
    private const val MAX_HISTORY_SIZE = 50

    // Standard deviation of odometry (tune these for actual robot)
    private val Q = Matrix3x3(
        0.01, 0.0,  0.0,
        0.0,  0.01, 0.0,
        0.0,  0.0,  0.01
    )

    // Known AprilTag coordinates for distance calculations
    private val TAGS = mapOf(
        1 to Pose3d(Translation3d(1.8, 1.8, 0.5), Rotation3d(0.0, 0.0, Math.PI)),
        2 to Pose3d(Translation3d(1.8, -1.8, 0.5), Rotation3d(0.0, 0.0, Math.PI)),
        3 to Pose3d(Translation3d(-1.8, 1.8, 0.5), Rotation3d(0.0, 0.0, 0.0)),
        4 to Pose3d(Translation3d(-1.8, -1.8, 0.5), Rotation3d(0.0, 0.0, 0.0))
    )

    /**
     * Integrates a new high-frequency wheel odometry observation delta into the state estimator.
     *
     * Processes state propagation ($x_{k} = f(x_{k-1}, u)$) and covariance updates. If the robot
     * experiences excessive roll/pitch (beaching) or high wheel slippage (scaled dynamically by gyro rate mismatch),
     * the system expands the process noise covariance matrix ($Q$) to discount wheel inputs.
     *
     * @param state The current state estimator state.
     * @param timestampMs The system timestamp in milliseconds when the sensors were read.
     * @param deltaTranslation The relative change in longitudinal and lateral distance since the last update.
     * @param deltaHeading The relative change in orientation since the last update.
     * @param pitchDegrees Current pitch tilt of the chassis in degrees.
     * @param rollDegrees Current roll tilt of the chassis in degrees.
     * @param gyroRateRadPerSec The angular velocity reading from the IMU in radians per second.
     * @param dtSeconds Elapsed time since the last frame update.
     * @return The updated [PoseEstimatorState] with new estimated pose, covariance, and rolling history entry.
     */
    fun addOdometryObservation(
        state: PoseEstimatorState,
        timestampMs: Long,
        deltaTranslation: Translation2d,
        deltaHeading: Rotation2d,
        pitchDegrees: Double = 0.0,
        rollDegrees: Double = 0.0,
        pitchVelocityDegPerSec: Double = 0.0,
        rollVelocityDegPerSec: Double = 0.0,
        gyroRateRadPerSec: Double = 0.0,
        dtSeconds: Double = 0.02
    ): PoseEstimatorState {
        val tiltDegrees = kotlin.math.sqrt(pitchDegrees * pitchDegrees + rollDegrees * rollDegrees)
        val tiltVelocity = kotlin.math.sqrt(pitchVelocityDegPerSec * pitchVelocityDegPerSec + rollVelocityDegPerSec * rollVelocityDegPerSec)

        var currentlyBeached = state.isBeached
        var unbeachedTime = state.lastUnbeachedTimeMs

        // Hysteresis logic
        if (!currentlyBeached && tiltDegrees > 15.0) {
            currentlyBeached = true
        } else if (currentlyBeached && tiltDegrees < 12.0) {
            currentlyBeached = false
            unbeachedTime = timestampMs
        }

        // Catastrophic tilt / beaching check: Freeze odometry updates
        if (currentlyBeached) {
            val newEntry = PoseHistoryEntry(timestampMs, state.estimatedPose, state.covariance)
            val newHistory = (state.history + newEntry).takeLast(MAX_HISTORY_SIZE)
            return state.copy(history = newHistory, isBeached = true, lastUnbeachedTimeMs = unbeachedTime)
        }

        val timeSinceUnbeachedMs = timestampMs - unbeachedTime
        val inRecovery = timeSinceUnbeachedMs < 500 && unbeachedTime != 0L

        // Continuous covariance scaling
        var tiltScale = 1.0
        if (tiltDegrees > 5.0) {
            val normalized = (tiltDegrees - 5.0) / 10.0 // 0.0 to 1.0
            val clamped = normalized.coerceIn(0.0, 1.0)
            tiltScale = 1.0 + 99.0 * (clamped * clamped)
        }

        // Impact prediction
        if (tiltVelocity > 20.0) {
            tiltScale = kotlin.math.max(tiltScale, 50.0)
        }

        // Post-beaching recovery forces max scale
        if (inRecovery) {
            tiltScale = 100.0
        }

        // Gyro rate mismatch check for wheel slippage detection
        val expectedHeadingVel = deltaHeading.radians / (if (dtSeconds > 1e-6) dtSeconds else 0.02)
        val slipScale = if (gyroRateRadPerSec != 0.0 && kotlin.math.abs(expectedHeadingVel - gyroRateRadPerSec) > 0.5) {
            10.0 // Dynamic wheel slippage covariance expansion
        } else {
            1.0
        }

        var currentQ = Q * tiltScale * slipScale

        val newHeading = Rotation2d(state.estimatedPose.heading.radians + deltaHeading.radians)
        val newPose = Pose2d(
            state.estimatedPose.x + deltaTranslation.x,
            state.estimatedPose.y + deltaTranslation.y,
            newHeading
        )
        
        // Simple propagation: P = P + dynamic Q
        val newCovariance = state.covariance + currentQ

        val newEntry = PoseHistoryEntry(timestampMs, newPose, newCovariance)
        val newHistory = (state.history + newEntry).takeLast(MAX_HISTORY_SIZE)

        return state.copy(
            estimatedPose = newPose,
            covariance = newCovariance,
            history = newHistory,
            isBeached = currentlyBeached,
            lastUnbeachedTimeMs = unbeachedTime
        )
    }

    /**
     * Fuses an asynchronous vision pose observation into the Extended Kalman Filter.
     *
     * Compensates for camera-to-system latency by searching the chronological [PoseHistoryEntry] list,
     * applying the measurement correction retroactively at the exact historical timestamp, and then
     * re-propagating subsequent odometry observations up to the current frame.
     *
     * Vision noise covariance $R$ scales quadratically with physical distance to targets, ensuring distant
     * noisy targets are weighted less than closer ones. An optional 3-DOF statistical Mahalanobis Distance
     * outlier filter rejects anomalous measurements (such as background reflections or bad tag decodes).
     *
     * @param state The current state estimator state.
     * @param measurement The vision measurement packet, including coordinates, target tag ID, and latency.
     * @param visionStdDevs Standard deviation [Vector3] (x, y, heading) representing measurement baseline noise.
     * @param numTags The number of tags visible in the frame (vision covariance scales by 1/sqrt(numTags)).
     * @param useMahalanobisRejection Enables statistical Mahalanobis distance outlier filtering.
     * @param mahalanobisThreshold Threshold beyond which vision measurements are rejected as outliers.
     * @return The updated [PoseEstimatorState] with new estimated pose, corrected covariance, and re-integrated history.
     */
    fun addVisionMeasurement(
        state: PoseEstimatorState,
        measurement: VisionMeasurement,
        visionStdDevs: Vector3, // Vector3(x, y, heading)
        numTags: Int = 1,
        useMahalanobisRejection: Boolean = true,
        mahalanobisThreshold: Double = 12.0
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

        // 1. Calculate physical distance to AprilTag
        val tagPose = TAGS[measurement.tagId]
        val distance = if (tagPose != null) {
            val dx = tagPose.x - baseEntry.pose.x
            val dy = tagPose.y - baseEntry.pose.y
            kotlin.math.sqrt(dx * dx + dy * dy)
        } else {
            // Fallback to measurement pose distance from robot base
            val measurementPose = measurement.targetPose.toPose2d()
            val dx = measurementPose.x - baseEntry.pose.x
            val dy = measurementPose.y - baseEntry.pose.y
            kotlin.math.sqrt(dx * dx + dy * dy)
        }

        // 2. Dynamic EKF vision noise scaling (multi-tag division & quadratic distance growth)
        val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())
        val distFactor = kotlin.math.sqrt(1.0 + distance * distance)
        val scaledStdDevs = visionStdDevs * (multiTagFactor * distFactor)

        // Extended Kalman Filter Update at the historical timestamp
        val R = Vector3(
            scaledStdDevs.x * scaledStdDevs.x,
            scaledStdDevs.y * scaledStdDevs.y,
            scaledStdDevs.z * scaledStdDevs.z
        ).let { v -> 
            Matrix3x3(
                v.x, 0.0, 0.0,
                0.0, v.y, 0.0,
                0.0, 0.0, v.z
            )
        }

        // Innovation residual y = z - Hx (where H is identity since we directly measure pose)
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

        // S = P + R (where H is Identity)
        val S = baseEntry.covariance + R
        val S_inv = S.inverse()

        // 3. Statistical Mahalanobis Distance Outlier Rejection
        if (useMahalanobisRejection) {
            val S_inv_y = S_inv * y
            val dMSquared = y.x * S_inv_y.x + y.y * S_inv_y.y + y.z * S_inv_y.z
            if (dMSquared > mahalanobisThreshold) {
                return state // Outlier rejected autonomously!
            }
        }

        // K = P * S^-1
        val K = baseEntry.covariance * S_inv

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
