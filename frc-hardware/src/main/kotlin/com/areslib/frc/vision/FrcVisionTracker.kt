package com.areslib.frc.vision

import com.areslib.action.RobotAction
import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.Store
import com.areslib.subsystem.VisionTracker
import com.areslib.telemetry.RobotStatusTracker

/**
 * FRC implementation of the [VisionTracker] interface.
 *
 * Polls Limelight/PhotonVision cameras for AprilTag measurements,
 * feeds valid observations into the CTRE swerve pose estimator (when on real hardware),
 * and dispatches vision data to the Redux store for EKF fusion.
 */
class FrcVisionTracker(
    private val store: Store,
    val visionIO: VisionIO?,
    private val swerveIO: com.areslib.hardware.drive.SwerveHardwareIO?,
    private val isSimulation: Boolean
) : VisionTracker {

    val visionInputs = VisionIOInputs()

    private var _lastVisionStatus: String = "INIT"

    /**
     * Human-readable status string describing the last vision processing result.
     */
    override val lastVisionStatus: String
        get() = _lastVisionStatus

    /**
     * True if the vision sensor hardware is connected and responding.
     */
    override val isConnected: Boolean
        get() = visionIO != null && visionInputs.isConnected

    /**
     * Polls the vision inputs, passes vehicle state to the camera (for MegaTag2),
     * and streams valid measurements back to the drivetrain Kalman filter and Redux store.
     *
     * @param timestampMs Current timestamp from [com.areslib.util.RobotClock].
     */
    override fun update(timestampMs: Long) {
        visionIO?.let { io ->
            val drive = store.state.drive
            val yawRate = swerveIO?.yawRateDegreesPerSecond ?: Math.toDegrees(drive.angularVelocityRadiansPerSecond)
            val yaw = swerveIO?.rawGyroYawDegrees ?: Math.toDegrees(drive.odometryHeading)
            
            io.setOrientation(
                yawDegrees = yaw,
                yawRateDegPerSec = yawRate,
                pitchDegrees = drive.pitchDegrees,
                pitchRateDegPerSec = 0.0,
                rollDegrees = drive.rollDegrees,
                rollRateDegPerSec = 0.0,
                linearVelocityMps = Math.hypot(drive.xVelocityMetersPerSecond, drive.yVelocityMetersPerSecond)
            )
            io.updateInputs(visionInputs)
            if (visionInputs.measurements.isNotEmpty()) {
                val measurement = visionInputs.measurements[0]
                // Distance-based outlier rejection: skip fusion for far/ambiguous tags
                val distance = kotlin.math.abs(measurement.robotPoseTargetSpace.z)
                val ambiguity = measurement.ambiguity
                if (!isSimulation && swerveIO != null && distance < 6.0 && ambiguity < 0.3) {
                    try {
                        val pose = com.areslib.math.geometry.Pose2d(
                            measurement.targetPose.translation.x,
                            measurement.targetPose.translation.y,
                            com.areslib.math.geometry.Rotation2d(measurement.targetPose.rotation.z)
                        )
                        val latencyMs = timestampMs - measurement.timestampMs
                        val timestampSec = edu.wpi.first.wpilibj.Timer.getFPGATimestamp() - (latencyMs / 1000.0)
                        swerveIO.addVisionMeasurement(pose, timestampSec)
                    } catch (e: Throwable) {
                        System.err.println("FrcSwerveRobot: Failed to feed vision to SwerveDrivetrain: ${e.message}")
                    }
                }
                store.dispatch(RobotAction.VisionMeasurementsReceived(
                    visionInputs.measurements,
                    timestampMs,
                    null
                ))
                _lastVisionStatus = when {
                    distance >= 6.0 -> "REJECTED_FAR (${String.format("%.1f", distance)}m)"
                    ambiguity >= 0.3 -> "REJECTED_AMBIGUOUS (${String.format("%.2f", ambiguity)})"
                    else -> "ACCEPTED"
                }
            } else {
                _lastVisionStatus = "NO TARGET"
            }
            RobotStatusTracker.visionConnected = visionInputs.isConnected
        } ?: run {
            RobotStatusTracker.visionConnected = false
            _lastVisionStatus = "OFFLINE"
        }
    }
}

