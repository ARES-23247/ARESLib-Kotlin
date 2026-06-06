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
    val alliance: Alliance = Alliance.BLUE
)

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
    
    // FRC Marvin 19 Sub-States
    val flywheel: FlywheelState = FlywheelState(),
    val cowl: CowlState = CowlState(),
    val intake: IntakeState = IntakeState(),
    val feeder: FeederState = FeederState(),
    val climber: ClimberState = ClimberState(),
    val floor: FloorState = FloorState()
) {
    /** Returns true when the flywheel is within 5% of target RPM */
    val isFlywheelAtSpeed: Boolean
        get() = flywheelActive && flywheelRPM >= flywheelTargetRPM * 0.95
}

data class VisionMeasurement(
    val timestampMs: Long = 0L,
    val targetPose: Pose3d = Pose3d(),
    val tagId: Int = -1,
    val ambiguity: Double = 0.0
)

data class VisionState(
    val lastTargetTimestampMs: Long = 0L,
    val targetX: Double = 0.0,
    val targetY: Double = 0.0,
    val hasTarget: Boolean = false,
    val measurements: List<VisionMeasurement> = emptyList(),
    val filterConfig: com.areslib.hardware.vision.VisionFilterConfig = com.areslib.hardware.vision.VisionFilterConfig.ftcDefaults()
)

enum class Alliance {
    RED, BLUE
}

