package com.areslib.math.estimation

import com.areslib.state.VisionMeasurement
import com.areslib.math.InputMath
import com.areslib.math.FieldLayouts
import com.areslib.math.geometry.*

data class PoseHistoryEntry(
    var timestampMs: Long = 0L,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var headingRad: Double = 0.0,
    var covariance: Matrix3x3 = Matrix3x3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
    var qScale: Double = 1.0
) {
    var pose: Pose2d
        get() = Pose2d(x, y, Rotation2d(headingRad))
        set(value) {
            x = value.x
            y = value.y
            headingRad = value.heading.radians
        }
}

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

    fun addEntry(timestampMs: Long, pose: Pose2d, covariance: Matrix3x3, qScale: Double) {
        val entry = entries[head]
        entry.timestampMs = timestampMs
        entry.pose = pose
        entry.covariance.setTo(covariance)
        entry.qScale = qScale
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    fun deepCopy(): HistoryBuffer {
        val newBuf = HistoryBuffer(capacity)
        for (i in 0 until capacity) {
            val src = entries[i]
            val dest = newBuf.entries[i]
            dest.timestampMs = src.timestampMs
            dest.x = src.x
            dest.y = src.y
            dest.headingRad = src.headingRad
            dest.covariance.setTo(src.covariance)
            dest.qScale = src.qScale
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
            dest.x = src.x
            dest.y = src.y
            dest.headingRad = src.headingRad
            dest.covariance.setTo(src.covariance)
            dest.qScale = src.qScale
        }
    }
    
    // allow setting existing entry to avoid object creation
    fun updateEntry(index: Int, timestampMs: Long, pose: Pose2d, covariance: Matrix3x3, qScale: Double) {
        val entry = get(index)
        entry.timestampMs = timestampMs
        entry.pose = pose
        entry.covariance.setTo(covariance)
        entry.qScale = qScale
    }

    fun updateEntryDirect(index: Int, timestampMs: Long, x: Double, y: Double, headingRad: Double, covariance: Matrix3x3, qScale: Double) {
        val entry = get(index)
        entry.timestampMs = timestampMs
        entry.x = x
        entry.y = y
        entry.headingRad = headingRad
        entry.covariance.setTo(covariance)
        entry.qScale = qScale
    }

    companion object {
        private val pool = Array(256) { HistoryBuffer(50) }
        private var poolIndex = 0

        @Synchronized
        fun obtainCopy(src: HistoryBuffer): HistoryBuffer {
            val dest = pool[poolIndex]
            src.copyInto(dest)
            poolIndex = (poolIndex + 1) % 256
            return dest
        }
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
    val lastUnbeachedTimeMs: Long = 0L,
    val gyroBiasRadPerSec: Double = 0.0,
    val lastInnovationX: Double = 0.0,
    val lastInnovationY: Double = 0.0,
    val lastInnovationTheta: Double = 0.0,
    val lastKalmanGain: DoubleArray = DoubleArray(0),
    val lastMeasurementAccepted: Boolean = false,
    val lastRejectionReason: String? = null
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

    var qX: Double = 0.01
        set(value) {
            field = value
            Q.m00 = value
        }

    var qY: Double = 0.01
        set(value) {
            field = value
            Q.m11 = value
        }

    var qTheta: Double = 0.01
        set(value) {
            field = value
            Q.m22 = value
        }

    private val scratchQ = Matrix3x3()
    private val scratchR = Matrix3x3()
    private val scratchS = Matrix3x3()
    private val scratchSInv = Matrix3x3()
    private val scratchK = Matrix3x3()
    private val scratchCov = Matrix3x3()
    private val scratchCov2 = Matrix3x3()
    private val scratchHistory = HistoryBuffer(MAX_HISTORY_SIZE)
    private val kalmanGainPool = Array(16) { DoubleArray(9) }
    private var kalmanGainPoolIndex = 0

    // Known AprilTag coordinates for distance calculations (configurable via FieldLayouts)
    @JvmField
    var activeTags: Map<Int, Pose3d> = FieldLayouts.SQUARE_STANDARD_TAGS

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
        when {
            !currentlyBeached && tiltDegrees > 15.0 -> {
                currentlyBeached = true
            }
            currentlyBeached && tiltDegrees < 12.0 -> {
                currentlyBeached = false
                unbeachedTime = timestampMs
            }
        }

        // Catastrophic tilt / beaching check: Freeze odometry updates
        if (currentlyBeached) {
            val newHistory = HistoryBuffer.obtainCopy(state.history)
            newHistory.addEntry(timestampMs, state.estimatedPose, state.covariance, 1.0)
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

        // Online Gyro Bias Estimation & Bias Correction
        val isStationary = deltaTranslation.x == 0.0 && deltaTranslation.y == 0.0 && deltaHeading.radians == 0.0
        val alpha = 0.01 * dtSeconds
        val newBias = if (isStationary && gyroRateRadPerSec != 0.0) {
            state.gyroBiasRadPerSec * (1.0 - alpha) + gyroRateRadPerSec * alpha
        } else {
            state.gyroBiasRadPerSec
        }

        val correctedGyroRate = gyroRateRadPerSec - newBias
        val correctedDeltaHeading = if (isStationary) {
            0.0
        } else {
            deltaHeading.radians - newBias * dtSeconds
        }

        // Gyro rate mismatch check for wheel slippage detection
        val expectedHeadingVel = deltaHeading.radians / (if (dtSeconds > 1e-6) dtSeconds else 0.02)
        val slipScale = if (correctedGyroRate != 0.0 && kotlin.math.abs(expectedHeadingVel - correctedGyroRate) > 0.5) {
            10.0 // Dynamic wheel slippage covariance expansion
        } else {
            1.0
        }

        scratchQ.setTo(Q)
        scratchQ.multiplyInPlace(tiltScale * slipScale)

        val newHeading = Rotation2d(state.estimatedPose.heading.radians + correctedDeltaHeading)
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

        val newHistory = HistoryBuffer.obtainCopy(state.history)
        newHistory.addEntry(timestampMs, newPose, newCovariance, tiltScale * slipScale)

        return state.copy(
            estimatedPose = newPose,
            covariance = newCovariance,
            history = newHistory,
            isBeached = currentlyBeached,
            lastUnbeachedTimeMs = unbeachedTime,
            gyroBiasRadPerSec = newBias
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
        mahalanobisThreshold: Double = 12.0,
        maxAmbiguity: Double = 0.2
    ): PoseEstimatorState {
        if (state.history.isEmpty()) return state.copy(lastMeasurementAccepted = false, lastRejectionReason = "empty_history")
        
        // Outlier rejection: Reject high-ambiguity decodes instantly to prevent "pose-flipping"
        if (measurement.ambiguity > maxAmbiguity) return state.copy(lastMeasurementAccepted = false, lastRejectionReason = "high_ambiguity")
        
        if (numTags <= 0) return state.copy(lastMeasurementAccepted = false, lastRejectionReason = "no_tags")
        if (visionStdDevs.x.isNaN() || visionStdDevs.x.isInfinite() || 
            visionStdDevs.y.isNaN() || visionStdDevs.y.isInfinite() || 
            visionStdDevs.z.isNaN() || visionStdDevs.z.isInfinite()) return state.copy(lastMeasurementAccepted = false, lastRejectionReason = "invalid_std_devs")
        if (mahalanobisThreshold.isNaN() || mahalanobisThreshold.isInfinite() || mahalanobisThreshold <= 0.0) return state.copy(lastMeasurementAccepted = false, lastRejectionReason = "invalid_threshold")

        var closestIndex = -1
        for (i in state.history.size - 1 downTo 0) {
            if (state.history[i].timestampMs <= measurement.timestampMs) {
                closestIndex = i
                break
            }
        }

        if (closestIndex == -1) {
            // Vision measurement is too old or we have no history, ignore it
            return state.copy(lastMeasurementAccepted = false, lastRejectionReason = "vision_too_old")
        }

        val baseEntry = state.history[closestIndex]

        // 1. Calculate physical distance to AprilTag and incidence angle
        val tagPose = activeTags[measurement.tagId]
        var incidenceScale = 1.0
        val distance = if (tagPose != null) {
            val dx = tagPose.x - baseEntry.x
            val dy = tagPose.y - baseEntry.y
            val losHeading = kotlin.math.atan2(dy, dx)
            val phi = InputMath.wrapAngle(losHeading - tagPose.rotation.z)
            val cosPhi = kotlin.math.cos(phi)
            incidenceScale = 1.0 / (cosPhi * cosPhi).coerceIn(0.1, 10.0)
            kotlin.math.sqrt(dx * dx + dy * dy)
        } else {
            // Fallback to measurement pose distance from robot base
            val measurementPose = measurement.targetPose.toPose2d()
            val dx = measurementPose.x - baseEntry.x
            val dy = measurementPose.y - baseEntry.y
            kotlin.math.sqrt(dx * dx + dy * dy)
        }

        // 2. Dynamic EKF vision noise scaling (multi-tag division, quadratic distance growth, incidence angle, and ambiguity)
        val ambiguityScale = 1.0 + 10.0 * (measurement.ambiguity * measurement.ambiguity)
        val finalScale = incidenceScale * ambiguityScale

        val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())
        val distFactor = kotlin.math.sqrt(1.0 + distance * distance)
        val scaledStdDevsX = visionStdDevs.x * (multiTagFactor * distFactor * finalScale)
        val scaledStdDevsY = visionStdDevs.y * (multiTagFactor * distFactor * finalScale)
        val scaledStdDevsZ = visionStdDevs.z * (multiTagFactor * distFactor * finalScale)

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

        if (det.isNaN() || det.isInfinite() || kotlin.math.abs(det) < 1e-9) {
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
        val headingDiff = InputMath.wrapAngle(measurementPose2d.heading.radians - baseEntry.headingRad)

        val yX = measurementPose2d.x - baseEntry.x
        val yY = measurementPose2d.y - baseEntry.y
        val yZ = headingDiff

        // 3. Statistical Mahalanobis Distance Outlier Rejection
        val sInvYX = scratchSInv.m00 * yX + scratchSInv.m01 * yY + scratchSInv.m02 * yZ
        val sInvYY = scratchSInv.m10 * yX + scratchSInv.m11 * yY + scratchSInv.m12 * yZ
        val sInvYZ = scratchSInv.m20 * yX + scratchSInv.m21 * yY + scratchSInv.m22 * yZ

        if (useMahalanobisRejection) {
            val dMSquared = yX * sInvYX + yY * sInvYY + yZ * sInvYZ
            if (dMSquared > mahalanobisThreshold) {
                return state.copy(
                    lastMeasurementAccepted = false,
                    lastRejectionReason = "mahalanobis_rejected",
                    lastInnovationX = yX,
                    lastInnovationY = yY,
                    lastInnovationTheta = yZ
                )
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

        val newHistory = HistoryBuffer.obtainCopy(state.history)
        // Copy current state history to scratch history for mutating
        newHistory.copyInto(scratchHistory)

        // Now we must replay all odometry deltas from closestIndex + 1 to the end
        var currentX = baseEntry.x + dxX
        var currentY = baseEntry.y + dxY
        var currentHeadingRad = InputMath.wrapAngle(baseEntry.headingRad + dxZ)
        var currentCov = scratchCov

        scratchHistory.updateEntryDirect(closestIndex, baseEntry.timestampMs, currentX, currentY, currentHeadingRad, currentCov, baseEntry.qScale)

        for (i in (closestIndex + 1) until state.history.size) {
            val prevRaw = state.history[i - 1]
            val currRaw = state.history[i]
            
            // Calculate raw delta
            val deltaX = currRaw.x - prevRaw.x
            val deltaY = currRaw.y - prevRaw.y
            val deltaHeading = currRaw.headingRad - prevRaw.headingRad

            // Re-apply delta to our updated state
            currentX += deltaX
            currentY += deltaY
            currentHeadingRad = InputMath.wrapAngle(currentHeadingRad + deltaHeading)

            val scale = currRaw.qScale
            scratchCov2.m00 = currentCov.m00 + Q.m00 * scale
            scratchCov2.m01 = currentCov.m01 + Q.m01 * scale
            scratchCov2.m02 = currentCov.m02 + Q.m02 * scale
            scratchCov2.m10 = currentCov.m10 + Q.m10 * scale
            scratchCov2.m11 = currentCov.m11 + Q.m11 * scale
            scratchCov2.m12 = currentCov.m12 + Q.m12 * scale
            scratchCov2.m20 = currentCov.m20 + Q.m20 * scale
            scratchCov2.m21 = currentCov.m21 + Q.m21 * scale
            scratchCov2.m22 = currentCov.m22 + Q.m22 * scale
            
            scratchHistory.updateEntryDirect(i, state.history[i].timestampMs, currentX, currentY, currentHeadingRad, scratchCov2, currRaw.qScale)
            currentCov = scratchCov2
        }

        // Copy mutated scratch history back to newHistory in-place
        scratchHistory.copyInto(newHistory)

        val kg = kalmanGainPool[kalmanGainPoolIndex]
        kg[0] = scratchK.m00; kg[1] = scratchK.m01; kg[2] = scratchK.m02
        kg[3] = scratchK.m10; kg[4] = scratchK.m11; kg[5] = scratchK.m12
        kg[6] = scratchK.m20; kg[7] = scratchK.m21; kg[8] = scratchK.m22
        kalmanGainPoolIndex = (kalmanGainPoolIndex + 1) % 16

        return state.copy(
            estimatedPose = Pose2d(currentX, currentY, Rotation2d(currentHeadingRad)),
            covariance = Matrix3x3(currentCov.m00, currentCov.m01, currentCov.m02, currentCov.m10, currentCov.m11, currentCov.m12, currentCov.m20, currentCov.m21, currentCov.m22),
            history = newHistory,
            lastInnovationX = yX,
            lastInnovationY = yY,
            lastInnovationTheta = yZ,
            lastKalmanGain = kg,
            lastMeasurementAccepted = true,
            lastRejectionReason = null
        )
    }
}
