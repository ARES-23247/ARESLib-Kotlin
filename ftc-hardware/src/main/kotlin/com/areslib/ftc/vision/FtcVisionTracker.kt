package com.areslib.ftc.vision

import com.areslib.action.RobotAction
import com.areslib.ftc.PinpointIO
import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.subsystem.Store
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.math.Vector3

/**
 * Manages the robot's AprilTag vision tracking system.
 * Handles outlier rejection (using yaw, field boundary, distance, and EKF Mahalanobis checks),
 * and triggers EKF snap updates during initialization or recovery states.
 */
class FtcVisionTracker(
    private val store: Store,
    val limelightIO: VisionIO?,
    private val pinpointIO: PinpointIO?
) {
    val visionInputs = VisionIOInputs()
    var lastLimelightPose: Pose2d? = null
        private set
    var lastLimelightTimeMs = 0L
        private set
    var lastVisionStatus = "OFFLINE"
        private set
    private var consecutiveVisionRejections = 0
    var isInInit = true
    var hasInitializedPoseWithVision = false

    // Pre-allocated structure to guarantee Zero-GC compliance inside EKF verification loops
    private val stdDevs = Vector3(0.05, 0.05, 0.1)

    /**
     * Polls the vision sensors, performs outlier rejection, and updates the EKF store.
     * Triggers mathematical snaps if initial pose or kidnapped robot conditions are met.
     */
    fun update(timestamp: Long) {
        val io = limelightIO ?: run {
            com.areslib.telemetry.RobotStatusTracker.visionConnected = false
            com.areslib.telemetry.RobotStatusTracker.visionStatus = "OFFLINE"
            return
        }

        io.updateInputs(visionInputs)
        if (visionInputs.measurements.isEmpty()) {
            lastVisionStatus = "NO TARGET"
            com.areslib.telemetry.RobotStatusTracker.visionConnected = visionInputs.isConnected
            com.areslib.telemetry.RobotStatusTracker.visionStatus = lastVisionStatus
            return
        }

        val measurement = visionInputs.measurements[0]
        lastLimelightPose = measurement.targetPose.toPose2d()
        lastLimelightTimeMs = timestamp

        val robotPose = store.state.drive.poseEstimator.estimatedPose
        val robotHeading = robotPose.heading.radians
        val tagPose3d = measurement.targetPose
        val tagPose2d = tagPose3d.toPose2d()
        val dx = tagPose2d.x - robotPose.x
        val dy = tagPose2d.y - robotPose.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val tagYaw = tagPose3d.rotation.z
        val headingDiff = com.areslib.math.InputMath.wrapAngle(tagYaw - robotHeading)

        lastVisionStatus = checkVisionOutlierRejection(measurement, distance, headingDiff)

        // 1. One-time absolute snap during initialization to bypass outlier lockout
        if (isInInit) {
            if (!hasInitializedPoseWithVision && measurement.ambiguity < 0.05) {
                val snapPose = measurement.targetPose.toPose2d()
                pinpointIO?.initialize(snapPose, resetHardware = false)
                hasInitializedPoseWithVision = true
                lastVisionStatus = "INIT_ALIGN_SNAP"
                store.dispatch(RobotAction.PoseUpdate(
                    xMeters = snapPose.x,
                    yMeters = snapPose.y,
                    headingRadians = snapPose.heading.radians,
                    timestampMs = timestamp,
                    isReset = true
                ))
            }
        } else {
            // Kidnapped Robot Recovery (Active Play)
            val isRejected = lastVisionStatus.startsWith("REJ_")
            val isHighConfidence = measurement.ambiguity < 0.05
            val isStationary = store.state.drive.xVelocityMetersPerSecond == 0.0 &&
                               store.state.drive.yVelocityMetersPerSecond == 0.0 &&
                               store.state.drive.angularVelocityRadiansPerSecond == 0.0

            if (isRejected && isHighConfidence && isStationary) {
                consecutiveVisionRejections++
                if (consecutiveVisionRejections >= 10) {
                    val snapPose = measurement.targetPose.toPose2d()
                    pinpointIO?.initialize(snapPose, resetHardware = false)
                    consecutiveVisionRejections = 0
                    lastVisionStatus = "RESEED_SNAP"
                    store.dispatch(RobotAction.PoseUpdate(
                        xMeters = snapPose.x,
                        yMeters = snapPose.y,
                        headingRadians = snapPose.heading.radians,
                        timestampMs = timestamp,
                        isReset = true
                    ))
                }
            } else {
                consecutiveVisionRejections = 0
            }
        }

        store.dispatch(RobotAction.VisionMeasurementsReceived(
            visionInputs.measurements,
            timestamp,
            null
        ))

        com.areslib.telemetry.RobotStatusTracker.visionConnected = visionInputs.isConnected
        com.areslib.telemetry.RobotStatusTracker.visionStatus = lastVisionStatus
    }

    private fun checkVisionOutlierRejection(
        measurement: com.areslib.state.VisionMeasurement,
        distance: Double,
        headingDiff: Double
    ): String {
        val tagPose3d = measurement.targetPose
        val tagPose2d = tagPose3d.toPose2d()
        val filterConfig = store.state.vision.filterConfig

        return when {
            measurement.ambiguity > filterConfig.maxAmbiguity -> {
                String.format("REJ_AMBIG (%.2f > %.2f)", measurement.ambiguity, filterConfig.maxAmbiguity)
            }
            tagPose3d.x < filterConfig.minFieldX || tagPose3d.x > filterConfig.maxFieldX ||
            tagPose3d.y < filterConfig.minFieldY || tagPose3d.y > filterConfig.maxFieldY ||
            tagPose3d.z < filterConfig.minFieldZ || tagPose3d.z > filterConfig.maxFieldZ -> {
                String.format("REJ_BOUNDS (Z: %.2f)", tagPose3d.z)
            }
            distance > filterConfig.maxDistanceMeters -> {
                String.format("REJ_DIST (%.2fm > %.2fm)", distance, filterConfig.maxDistanceMeters)
            }
            kotlin.math.abs(headingDiff) > filterConfig.maxRotationDeviationRad -> {
                String.format("REJ_YAW (%.1f° > %.1f°)", Math.toDegrees(headingDiff), Math.toDegrees(filterConfig.maxRotationDeviationRad))
            }
            else -> {
                // Dry run of EKF Mahalanobis distance checks using pre-allocated stdDev vector
                val currentEstimator = store.state.drive.poseEstimator
                if (currentEstimator.history.isNotEmpty()) {
                    val closestIndex = currentEstimator.history.indices.reversed().firstOrNull {
                        currentEstimator.history[it].timestampMs <= measurement.timestampMs
                    } ?: -1
                    if (closestIndex != -1) {
                        val baseEntry = currentEstimator.history[closestIndex]
                        val numTags = visionInputs.measurements.size
                        val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())
                        val distFactor = kotlin.math.sqrt(1.0 + distance * distance)
                        
                        val scaledStdDevsX = stdDevs.x * (multiTagFactor * distFactor)
                        val scaledStdDevsY = stdDevs.y * (multiTagFactor * distFactor)
                        val scaledStdDevsZ = stdDevs.z * (multiTagFactor * distFactor)
                        
                        val rXX = scaledStdDevsX * scaledStdDevsX
                        val rYY = scaledStdDevsY * scaledStdDevsY
                        val rZZ = scaledStdDevsZ * scaledStdDevsZ
                        
                        val sXX = baseEntry.covariance.m00 + rXX
                        val sYY = baseEntry.covariance.m11 + rYY
                        val sZZ = baseEntry.covariance.m22 + rZZ
                        
                        val yX = tagPose2d.x - baseEntry.pose.x
                        val yY = tagPose2d.y - baseEntry.pose.y
                        val yZ = com.areslib.math.InputMath.wrapAngle(tagPose2d.heading.radians - baseEntry.pose.heading.radians)
                        
                        val dMSquared = (yX * yX / sXX) + (yY * yY / sYY) + (yZ * yZ / sZZ)
                        if (dMSquared > 12.0) {
                            String.format("REJ_MAHALANOBIS (%.2f > 12.0)", dMSquared)
                        } else {
                            "ACCEPTED"
                        }
                    } else {
                        "ACCEPTED (NO_HIST)"
                    }
                } else {
                    "ACCEPTED"
                }
            }
        }
    }
}
