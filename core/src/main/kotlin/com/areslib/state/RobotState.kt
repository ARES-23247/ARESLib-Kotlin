package com.areslib.state

import com.areslib.math.Pose3d
import com.areslib.math.PoseEstimatorState

/**
 * Represents a detected obstacle on the field.
 */
data class Obstacle(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val radius: Double = 0.2
)

/**
 * Stores the dynamic costmap of fused local range observations.
 */
data class CostmapState(
    val obstacles: List<Obstacle> = emptyList(),
    val lastUpdateTimestampMs: Long = 0L
)

/**
 * The root immutable state tree for the entire robot.
 */
data class RobotState(
    val drive: DriveState = DriveState(),
    val superstructure: SuperstructureState = SuperstructureState(),
    val vision: VisionState = VisionState(),
    val costmap: CostmapState = CostmapState(),
    val pathState: PathState = PathState(),
    val timestampMs: Long = 0L
)

enum class DriveMode {
    TELEOP,
    HEADING_HOLD,
    X_BRAKE
}

data class DriveState(
    val xVelocityMetersPerSecond: Double = 0.0,
    val yVelocityMetersPerSecond: Double = 0.0,
    val angularVelocityRadiansPerSecond: Double = 0.0,
    val odometryX: Double = 0.0,
    val odometryY: Double = 0.0,
    val odometryHeading: Double = 0.0,
    val poseEstimator: PoseEstimatorState = PoseEstimatorState(),
    val pitchDegrees: Double = 0.0,
    val rollDegrees: Double = 0.0,
    val xAccelerationG: Double = 0.0,
    val yAccelerationG: Double = 0.0,
    val zAccelerationG: Double = 0.0,
    val driveMode: DriveMode = DriveMode.TELEOP,
    val headingLockTargetRadians: Double? = null,
    val isFieldCentric: Boolean = true,
    val alliance: Alliance = Alliance.BLUE,
    // EKF diagnostics:
    val covarianceMatrix: List<Double> = emptyList(),
    val ekfDriftX: Double = 0.0,
    val ekfDriftY: Double = 0.0,
    val lastInnovationX: Double = 0.0,
    val lastInnovationY: Double = 0.0,
    val lastInnovationTheta: Double = 0.0,
    val lastKalmanGain: List<Double> = emptyList(),
    val rawOdometryX: Double = 0.0,
    val rawOdometryY: Double = 0.0,
    val rawOdometryHeading: Double = 0.0
) {
    fun updateDiagnostics(odomX: Double, odomY: Double, odomHeading: Double, updatedEstimator: PoseEstimatorState): DriveState {
        val covMatrix = listOf(
            updatedEstimator.covariance.m00, updatedEstimator.covariance.m01, updatedEstimator.covariance.m02,
            updatedEstimator.covariance.m10, updatedEstimator.covariance.m11, updatedEstimator.covariance.m12,
            updatedEstimator.covariance.m20, updatedEstimator.covariance.m21, updatedEstimator.covariance.m22
        )
        return this.copy(
            poseEstimator = updatedEstimator,
            covarianceMatrix = covMatrix,
            ekfDriftX = odomX - updatedEstimator.estimatedPose.x,
            ekfDriftY = odomY - updatedEstimator.estimatedPose.y,
            rawOdometryX = odomX,
            rawOdometryY = odomY,
            rawOdometryHeading = odomHeading,
            lastInnovationX = updatedEstimator.lastInnovationX,
            lastInnovationY = updatedEstimator.lastInnovationY,
            lastInnovationTheta = updatedEstimator.lastInnovationTheta,
            lastKalmanGain = updatedEstimator.lastKalmanGain
        )
    }
}

/**
 * Superstructure finite state machine states.
 */
enum class SuperstructureMode {
    /** No superstructure motors active */
    IDLE,
    /** Intake motor running, picking up balls */
    INTAKING,
    /** Flywheel ramping up to target RPM */
    FLYWHEEL_SPINUP,
    /** Flywheel at target RPM, ready to shoot */
    FLYWHEEL_READY,
    /** Transfer motor feeding ball to flywheel (only allowed when flywheel is ready) */
    SHOOTING
}

data class SuperstructureState(
    val mode: SuperstructureMode = SuperstructureMode.IDLE,
    val intakeActive: Boolean = false,
    val flywheelActive: Boolean = false,
    val transferActive: Boolean = false,
    val flywheelRPM: Double = 0.0,
    val flywheelTargetRPM: Double = 4000.0,
    val inventoryCount: Int = 0,
    val elevatorHeightMeters: Double = 0.0,
    
    // Custom extensible container for season/robot-specific states
    val custom: Any? = null,
    val states: Map<String, Any> = emptyMap()
) {
    inline fun <reified T> getSubstate(key: String): T? = states[key] as? T

    inline fun <reified T> updateState(key: String, block: T.() -> T): SuperstructureState {
        val current = (states[key] as? T) ?: error("Substate for key '$key' was not found or has incorrect type")
        val updated = current.block()
        return this.copy(states = this.states + (key to updated as Any))
    }

    /** Returns true when the flywheel is within 5% of target RPM */
    val isFlywheelAtSpeed: Boolean
        get() = flywheelActive && flywheelRPM >= flywheelTargetRPM * 0.95
}

/**
 * A single AprilTag fiducial detection from the vision subsystem.
 *
 * @property timestampMs Capture timestamp in milliseconds from [com.areslib.util.RobotClock].
 * @property targetPose The tag's 3D pose in the robot's coordinate frame (meters, radians).
 * @property tagId The detected AprilTag's numeric fiducial ID.
 * @property ambiguity PnP pose ambiguity score (0.0 = perfect, >0.2 = unreliable).
 * @property robotPoseTargetSpace The robot's 3D pose expressed in **target-space** coordinates.
 *
 *   Target-space coordinate frame (origin at the center of the AprilTag face):
 *   - **X+**: to the right of the tag (when facing the tag)
 *   - **Y+**: upward from the tag
 *   - **Z+**: outward from the tag face (toward the observer/camera)
 *   - **Yaw+**: counter-clockwise rotation when viewed from above
 *
 *   This pose is the raw output from Limelight's `robotPoseTargetSpace` pipeline result.
 *   It is used by the alignment controller to compute translational and rotational errors
 *   for driving the robot square to the tag at a desired standoff distance.
 */
data class VisionMeasurement(
    val timestampMs: Long = 0L,
    val targetPose: Pose3d = Pose3d(),
    val tagId: Int = -1,
    val ambiguity: Double = 0.0,
    val robotPoseTargetSpace: Pose3d = Pose3d()
)

data class VisionState(
    val lastTargetTimestampMs: Long = 0L,
    val targetX: Double = 0.0,
    val targetY: Double = 0.0,
    val hasTarget: Boolean = false,
    val measurements: List<VisionMeasurement> = emptyList(),
    val filterConfig: com.areslib.hardware.vision.VisionFilterConfig = com.areslib.hardware.vision.VisionFilterConfig.ftcDefaults(),
    // EKF diagnostics:
    val lastMeasurementAccepted: Boolean = false,
    val lastRejectionReason: String? = null,
    val covarianceBeforeUpdate: List<Double>? = null,
    val covarianceAfterUpdate: List<Double>? = null,
    val measurementCount: Int = 0,
    val rejectionCount: Int = 0
)

enum class Alliance {
    RED, BLUE
}

