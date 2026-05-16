package com.areslib.state

/**
 * The root immutable state tree for the entire robot.
 */
data class RobotState(
    val drive: DriveState = DriveState(),
    val superstructure: SuperstructureState = SuperstructureState(),
    val vision: VisionState = VisionState(),
    val timestampMs: Long = 0L
)

data class DriveState(
    val xVelocityMetersPerSecond: Double = 0.0,
    val yVelocityMetersPerSecond: Double = 0.0,
    val angularVelocityRadiansPerSecond: Double = 0.0,
    val odometryX: Double = 0.0,
    val odometryY: Double = 0.0,
    val odometryHeading: Double = 0.0
)

data class SuperstructureState(
    val elevatorHeightMeters: Double = 0.0,
    val intakeActive: Boolean = false
)

data class VisionState(
    val lastTargetTimestampMs: Long = 0L,
    val targetX: Double = 0.0,
    val targetY: Double = 0.0,
    val hasTarget: Boolean = false
)
