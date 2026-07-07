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
    val covarianceMatrix: DoubleArray = DoubleArray(0),
    val ekfDriftX: Double = 0.0,
    val ekfDriftY: Double = 0.0,
    val lastInnovationX: Double = 0.0,
    val lastInnovationY: Double = 0.0,
    val lastInnovationTheta: Double = 0.0,
    val lastKalmanGain: DoubleArray = DoubleArray(0),
    val rawOdometryX: Double = 0.0,
    val rawOdometryY: Double = 0.0,
    val rawOdometryHeading: Double = 0.0
) {
    fun updateDiagnostics(odomX: Double, odomY: Double, odomHeading: Double, updatedEstimator: PoseEstimatorState): DriveState {
        var cov = this.covarianceMatrix
        if (cov.size != 9) {
            cov = DoubleArray(9)
        }
        cov[0] = updatedEstimator.covariance.m00
        cov[1] = updatedEstimator.covariance.m01
        cov[2] = updatedEstimator.covariance.m02
        cov[3] = updatedEstimator.covariance.m10
        cov[4] = updatedEstimator.covariance.m11
        cov[5] = updatedEstimator.covariance.m12
        cov[6] = updatedEstimator.covariance.m20
        cov[7] = updatedEstimator.covariance.m21
        cov[8] = updatedEstimator.covariance.m22

        return this.copy(
            poseEstimator = updatedEstimator,
            covarianceMatrix = cov,
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
 *   ## Target-Space Coordinate Frame
 *   Origin is at the center of the AprilTag face:
 *   - **X+**: to the right of the tag (when facing the tag)
 *   - **Y+**: upward from the tag (vertical axis)
 *   - **Z+**: outward from the tag face (toward the observer/camera) — this is the depth/distance axis
 *
 *   ## Translation Access
 *   - `robotPoseTargetSpace.x` → lateral offset (positive = robot is right of tag center)
 *   - `robotPoseTargetSpace.y` → vertical offset (usually near 0 for ground robots)
 *   - `robotPoseTargetSpace.z` → distance from tag face (always positive, in meters)
 *
 *   ## ⚠️ CRITICAL: Rotation Axis Mapping (Limelight → Rotation3d)
 *   The Limelight SDK reports roll/pitch/yaw in FTC conventions, but target-space
 *   has a **different vertical axis** (Y-up) than FTC field space (Z-up). The raw
 *   Limelight euler angles are passed directly into `Rotation3d(roll, pitch, yaw)`
 *   WITHOUT a coordinate transform (see [FtcLimelightIO]). This creates a mismatch:
 *
 *   | Physical Rotation          | Limelight SDK Call | Rotation3d Property | Euler Axis |
 *   |----------------------------|--------------------|---------------------|------------|
 *   | Robot tilting sideways     | `getRoll()`        | `rotation.x`        | X (roll)   |
 *   | **Robot heading (yaw)**    | `getPitch()`       | **`rotation.y`**    | Y (pitch)  |
 *   | Robot tilting forward/back | `getYaw()`         | `rotation.z`        | Z (yaw)    |
 *
 *   **The robot's heading rotation relative to the tag (left/right turning) is in
 *   `rotation.y`, NOT `rotation.z`.** This is because in target-space the vertical
 *   axis is Y, so heading rotation is around Y. The Limelight SDK's `getPitch()`
 *   returns this value, which maps to `Rotation3d.y`.
 *
 *   **Sign convention:** Negate `rotation.y` for standard CCW-positive heading:
 *   ```kotlin
 *   val robotYaw = -robotPoseTargetSpace.rotation.y  // heading relative to tag
 *   ```
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
    val covarianceBeforeUpdate: DoubleArray? = null,
    val covarianceAfterUpdate: DoubleArray? = null,
    val measurementCount: Int = 0,
    val rejectionCount: Int = 0
)

enum class Alliance {
    RED, BLUE
}

