package com.areslib.math

import com.areslib.state.VisionMeasurement

data class PoseHistoryEntry(
    var timestampMs: Long = 0L,
    var pose: Pose2d = Pose2d(),
    var covariance: Matrix3x3 = Matrix3x3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
)

class HistoryBuffer(private val capacity: Int = 50) : AbstractList<PoseHistoryEntry>() {
    private val entries = Array(capacity) { PoseHistoryEntry() }
    private var head = 0
    private var count = 0

    override val size: Int get() = count

    override fun get(index: Int): PoseHistoryEntry {
        if (index < 0 || index >= count) throw IndexOutOfBoundsException("Index: $index, Size: $count")
        val physicalIndex = if (count == capacity) (head + index) % capacity else index
        return entries[physicalIndex]
    }

    fun addEntry(timestampMs: Long, pose: Pose2d, covariance: Matrix3x3) {
        val entry = entries[head]
        entry.timestampMs = timestampMs
        entry.pose = pose
        entry.covariance.setTo(covariance)
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    fun deepCopy(): HistoryBuffer {
        val newBuf = HistoryBuffer(capacity)
        for (i in 0 until capacity) {
            val src = entries[i]
            val dest = newBuf.entries[i]
            dest.timestampMs = src.timestampMs
            dest.pose = src.pose
            dest.covariance.setTo(src.covariance)
        }
        newBuf.head = head
        newBuf.count = count
        return newBuf
    }

    fun copyInto(destination: HistoryBuffer) {
        destination.head = this.head
        destination.count = this.count
        for (i in 0 until capacity) {
            val src = this.entries[i]
            val dest = destination.entries[i]
            dest.timestampMs = src.timestampMs
            dest.pose = src.pose
            dest.covariance.setTo(src.covariance)
        }
    }
    
    // allow setting existing entry to avoid object creation
    fun updateEntry(index: Int, timestampMs: Long, pose: Pose2d, covariance: Matrix3x3) {
        val entry = get(index)
        entry.timestampMs = timestampMs
        entry.pose = pose
        entry.covariance.setTo(covariance)
    }
}

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
    val covariance: Matrix3x3 = Matrix3x3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
    val history: HistoryBuffer = HistoryBuffer(50), // Max size typically ~50
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

    private val scratchQ = Matrix3x3()
    private val scratchR = Matrix3x3()
    private val scratchS = Matrix3x3()
    private val scratchSInv = Matrix3x3()
    private val scratchK = Matrix3x3()
    private val scratchCov = Matrix3x3()
    private val scratchCov2 = Matrix3x3()
    private val scratchHistory = HistoryBuffer(MAX_HISTORY_SIZE)

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
        if (deltaTranslation.x.isNaN() || deltaTranslation.x.isInfinite() ||
            deltaTranslation.y.isNaN() || deltaTranslation.y.isInfinite() ||
            deltaHeading.radians.isNaN() || deltaHeading.radians.isInfinite() ||
            pitchDegrees.isNaN() || pitchDegrees.isInfinite() ||
            rollDegrees.isNaN() || rollDegrees.isInfinite() ||
            pitchVelocityDegPerSec.isNaN() || pitchVelocityDegPerSec.isInfinite() ||
            rollVelocityDegPerSec.isNaN() || rollVelocityDegPerSec.isInfinite() ||
            gyroRateRadPerSec.isNaN() || gyroRateRadPerSec.isInfinite() ||
            dtSeconds.isNaN() || dtSeconds.isInfinite()
        ) {
            return state
        }

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
            val history = state.history
            history.addEntry(timestampMs, state.estimatedPose, state.covariance)
            return state.copy(isBeached = true, lastUnbeachedTimeMs = unbeachedTime)
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

        scratchQ.setTo(Q)
        scratchQ.multiplyInPlace(tiltScale * slipScale)

        val newHeading = Rotation2d(state.estimatedPose.heading.radians + deltaHeading.radians)
        val newPose = Pose2d(
            state.estimatedPose.x + deltaTranslation.x,
            state.estimatedPose.y + deltaTranslation.y,
            newHeading
        )
        
        // Simple propagation: P = P + dynamic Q
        val newCovariance = Matrix3x3(
            state.covariance.m00 + scratchQ.m00, state.covariance.m01 + scratchQ.m01, state.covariance.m02 + scratchQ.m02,
            state.covariance.m10 + scratchQ.m10, state.covariance.m11 + scratchQ.m11, state.covariance.m12 + scratchQ.m12,
            state.covariance.m20 + scratchQ.m20, state.covariance.m21 + scratchQ.m21, state.covariance.m22 + scratchQ.m22
        )

        val history = state.history
        history.addEntry(timestampMs, newPose, newCovariance)

        return state.copy(
            estimatedPose = newPose,
            covariance = newCovariance,
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
        
        if (numTags <= 0) return state
        if (visionStdDevs.x.isNaN() || visionStdDevs.x.isInfinite() || 
            visionStdDevs.y.isNaN() || visionStdDevs.y.isInfinite() || 
            visionStdDevs.z.isNaN() || visionStdDevs.z.isInfinite()) return state
        if (mahalanobisThreshold.isNaN() || mahalanobisThreshold.isInfinite() || mahalanobisThreshold <= 0.0) return state

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
        val scaledStdDevsX = visionStdDevs.x * (multiTagFactor * distFactor)
        val scaledStdDevsY = visionStdDevs.y * (multiTagFactor * distFactor)
        val scaledStdDevsZ = visionStdDevs.z * (multiTagFactor * distFactor)

        // Extended Kalman Filter Update at the historical timestamp
        scratchR.m00 = scaledStdDevsX * scaledStdDevsX; scratchR.m01 = 0.0; scratchR.m02 = 0.0
        scratchR.m10 = 0.0; scratchR.m11 = scaledStdDevsY * scaledStdDevsY; scratchR.m12 = 0.0
        scratchR.m20 = 0.0; scratchR.m21 = 0.0; scratchR.m22 = scaledStdDevsZ * scaledStdDevsZ

        // S = P + R (where H is Identity)
        scratchS.m00 = baseEntry.covariance.m00 + scratchR.m00
        scratchS.m01 = baseEntry.covariance.m01
        scratchS.m02 = baseEntry.covariance.m02
        scratchS.m10 = baseEntry.covariance.m10
        scratchS.m11 = baseEntry.covariance.m11 + scratchR.m11
        scratchS.m12 = baseEntry.covariance.m12
        scratchS.m20 = baseEntry.covariance.m20
        scratchS.m21 = baseEntry.covariance.m21
        scratchS.m22 = baseEntry.covariance.m22 + scratchR.m22

        // S_inv inversion
        val det = scratchS.m00 * (scratchS.m11 * scratchS.m22 - scratchS.m12 * scratchS.m21) -
                  scratchS.m01 * (scratchS.m10 * scratchS.m22 - scratchS.m12 * scratchS.m20) +
                  scratchS.m02 * (scratchS.m10 * scratchS.m21 - scratchS.m11 * scratchS.m20)

        if (kotlin.math.abs(det) < 1e-9) {
            scratchSInv.m00 = 0.0; scratchSInv.m01 = 0.0; scratchSInv.m02 = 0.0
            scratchSInv.m10 = 0.0; scratchSInv.m11 = 0.0; scratchSInv.m12 = 0.0
            scratchSInv.m20 = 0.0; scratchSInv.m21 = 0.0; scratchSInv.m22 = 0.0
        } else {
            val invDet = 1.0 / det
            scratchSInv.m00 =  (scratchS.m11 * scratchS.m22 - scratchS.m12 * scratchS.m21) * invDet
            scratchSInv.m01 = -(scratchS.m01 * scratchS.m22 - scratchS.m02 * scratchS.m21) * invDet
            scratchSInv.m02 =  (scratchS.m01 * scratchS.m12 - scratchS.m02 * scratchS.m11) * invDet
            
            scratchSInv.m10 = -(scratchS.m10 * scratchS.m22 - scratchS.m12 * scratchS.m20) * invDet
            scratchSInv.m11 =  (scratchS.m00 * scratchS.m22 - scratchS.m02 * scratchS.m20) * invDet
            scratchSInv.m12 = -(scratchS.m00 * scratchS.m12 - scratchS.m02 * scratchS.m10) * invDet
            
            scratchSInv.m20 =  (scratchS.m10 * scratchS.m21 - scratchS.m11 * scratchS.m20) * invDet
            scratchSInv.m21 = -(scratchS.m00 * scratchS.m21 - scratchS.m01 * scratchS.m20) * invDet
            scratchSInv.m22 =  (scratchS.m00 * scratchS.m11 - scratchS.m01 * scratchS.m10) * invDet
        }

        // Innovation residual y = z - Hx (where H is identity since we directly measure pose)
        val measurementPose2d = measurement.targetPose.toPose2d()
        val headingDiff = InputMath.wrapAngle(measurementPose2d.heading.radians - baseEntry.pose.heading.radians)

        val yX = measurementPose2d.x - baseEntry.pose.x
        val yY = measurementPose2d.y - baseEntry.pose.y
        val yZ = headingDiff

        // 3. Statistical Mahalanobis Distance Outlier Rejection
        val sInvYX = scratchSInv.m00 * yX + scratchSInv.m01 * yY + scratchSInv.m02 * yZ
        val sInvYY = scratchSInv.m10 * yX + scratchSInv.m11 * yY + scratchSInv.m12 * yZ
        val sInvYZ = scratchSInv.m20 * yX + scratchSInv.m21 * yY + scratchSInv.m22 * yZ

        if (useMahalanobisRejection) {
            val dMSquared = yX * sInvYX + yY * sInvYY + yZ * sInvYZ
            if (dMSquared > mahalanobisThreshold) {
                return state // Outlier rejected autonomously!
            }
        }

        // K = P * S^-1
        scratchK.m00 = baseEntry.covariance.m00 * scratchSInv.m00 + baseEntry.covariance.m01 * scratchSInv.m10 + baseEntry.covariance.m02 * scratchSInv.m20
        scratchK.m01 = baseEntry.covariance.m00 * scratchSInv.m01 + baseEntry.covariance.m01 * scratchSInv.m11 + baseEntry.covariance.m02 * scratchSInv.m21
        scratchK.m02 = baseEntry.covariance.m00 * scratchSInv.m02 + baseEntry.covariance.m01 * scratchSInv.m12 + baseEntry.covariance.m02 * scratchSInv.m22
        
        scratchK.m10 = baseEntry.covariance.m10 * scratchSInv.m00 + baseEntry.covariance.m11 * scratchSInv.m10 + baseEntry.covariance.m12 * scratchSInv.m20
        scratchK.m11 = baseEntry.covariance.m10 * scratchSInv.m01 + baseEntry.covariance.m11 * scratchSInv.m11 + baseEntry.covariance.m12 * scratchSInv.m21
        scratchK.m12 = baseEntry.covariance.m10 * scratchSInv.m02 + baseEntry.covariance.m11 * scratchSInv.m12 + baseEntry.covariance.m12 * scratchSInv.m22
        
        scratchK.m20 = baseEntry.covariance.m20 * scratchSInv.m00 + baseEntry.covariance.m21 * scratchSInv.m10 + baseEntry.covariance.m22 * scratchSInv.m20
        scratchK.m21 = baseEntry.covariance.m20 * scratchSInv.m01 + baseEntry.covariance.m21 * scratchSInv.m11 + baseEntry.covariance.m22 * scratchSInv.m21
        scratchK.m22 = baseEntry.covariance.m20 * scratchSInv.m02 + baseEntry.covariance.m21 * scratchSInv.m12 + baseEntry.covariance.m22 * scratchSInv.m22

        // Updated state delta: dx = K * y
        val dxX = scratchK.m00 * yX + scratchK.m01 * yY + scratchK.m02 * yZ
        val dxY = scratchK.m10 * yX + scratchK.m11 * yY + scratchK.m12 * yZ
        val dxZ = scratchK.m20 * yX + scratchK.m21 * yY + scratchK.m22 * yZ

        // Updated covariance: P = (I - K) * P => scratchCov = scratchK * baseEntry.covariance
        scratchCov.m00 = scratchK.m00 * baseEntry.covariance.m00 + scratchK.m01 * baseEntry.covariance.m10 + scratchK.m02 * baseEntry.covariance.m20
        scratchCov.m01 = scratchK.m00 * baseEntry.covariance.m01 + scratchK.m01 * baseEntry.covariance.m11 + scratchK.m02 * baseEntry.covariance.m21
        scratchCov.m02 = scratchK.m00 * baseEntry.covariance.m02 + scratchK.m01 * baseEntry.covariance.m12 + scratchK.m02 * baseEntry.covariance.m22
        
        scratchCov.m10 = scratchK.m10 * baseEntry.covariance.m00 + scratchK.m11 * baseEntry.covariance.m10 + scratchK.m12 * baseEntry.covariance.m20
        scratchCov.m11 = scratchK.m10 * baseEntry.covariance.m01 + scratchK.m11 * baseEntry.covariance.m11 + scratchK.m12 * baseEntry.covariance.m21
        scratchCov.m12 = scratchK.m10 * baseEntry.covariance.m02 + scratchK.m11 * baseEntry.covariance.m12 + scratchK.m12 * baseEntry.covariance.m22
        
        scratchCov.m20 = scratchK.m20 * baseEntry.covariance.m00 + scratchK.m21 * baseEntry.covariance.m10 + scratchK.m22 * baseEntry.covariance.m20
        scratchCov.m21 = scratchK.m20 * baseEntry.covariance.m01 + scratchK.m21 * baseEntry.covariance.m11 + scratchK.m22 * baseEntry.covariance.m21
        scratchCov.m22 = scratchK.m20 * baseEntry.covariance.m02 + scratchK.m21 * baseEntry.covariance.m12 + scratchK.m22 * baseEntry.covariance.m22

        val cov00 = baseEntry.covariance.m00 - scratchCov.m00
        val cov01 = baseEntry.covariance.m01 - scratchCov.m01
        val cov02 = baseEntry.covariance.m02 - scratchCov.m02
        val cov10 = baseEntry.covariance.m10 - scratchCov.m10
        val cov11 = baseEntry.covariance.m11 - scratchCov.m11
        val cov12 = baseEntry.covariance.m12 - scratchCov.m12
        val cov20 = baseEntry.covariance.m20 - scratchCov.m20
        val cov21 = baseEntry.covariance.m21 - scratchCov.m21
        val cov22 = baseEntry.covariance.m22 - scratchCov.m22

        scratchCov.m00 = cov00; scratchCov.m01 = cov01; scratchCov.m02 = cov02
        scratchCov.m10 = cov10; scratchCov.m11 = cov11; scratchCov.m12 = cov12
        scratchCov.m20 = cov20; scratchCov.m21 = cov21; scratchCov.m22 = cov22

        val updatedPose = Pose2d(
            baseEntry.pose.x + dxX,
            baseEntry.pose.y + dxY,
            Rotation2d(baseEntry.pose.heading.radians + dxZ)
        )

        // Copy current state history to scratch history for mutating
        state.history.copyInto(scratchHistory)

        // Now we must replay all odometry deltas from closestIndex + 1 to the end
        var currentPose = updatedPose
        var currentCov = scratchCov

        scratchHistory.updateEntry(closestIndex, baseEntry.timestampMs, currentPose, currentCov)

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

            scratchCov2.m00 = currentCov.m00 + Q.m00
            scratchCov2.m01 = currentCov.m01 + Q.m01
            scratchCov2.m02 = currentCov.m02 + Q.m02
            scratchCov2.m10 = currentCov.m10 + Q.m10
            scratchCov2.m11 = currentCov.m11 + Q.m11
            scratchCov2.m12 = currentCov.m12 + Q.m12
            scratchCov2.m20 = currentCov.m20 + Q.m20
            scratchCov2.m21 = currentCov.m21 + Q.m21
            scratchCov2.m22 = currentCov.m22 + Q.m22
            
            scratchHistory.updateEntry(i, state.history[i].timestampMs, currentPose, scratchCov2)
            currentCov = scratchCov2
        }

        // Copy mutated scratch history back to state history in-place
        scratchHistory.copyInto(state.history)

        return state.copy(
            estimatedPose = currentPose,
            covariance = Matrix3x3(currentCov.m00, currentCov.m01, currentCov.m02, currentCov.m10, currentCov.m11, currentCov.m12, currentCov.m20, currentCov.m21, currentCov.m22)
        )
    }
}
