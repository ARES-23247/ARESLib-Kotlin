package com.areslib.state

/**
 * Immutable representation of the dual-motor shooter flywheel state.
 */
data class FlywheelState(
    val velocityRpm: Double = 0.0,
    val targetVelocityRpm: Double = 0.0,
    val currentAmps: Double = 0.0,
    val tempCelsius: Double = 0.0
)

/**
 * Immutable representation of the adjustable cowl/hood angle state.
 */
data class CowlState(
    val angleDegrees: Double = 0.0,
    val targetAngleDegrees: Double = 0.0,
    val currentAmps: Double = 0.0
)

/**
 * Immutable representation of the active pivot-arm intake and roller state.
 */
data class IntakeState(
    val pivotAngleDegrees: Double = 0.0,
    val targetAngleDegrees: Double = 0.0,
    val rollerVelocityRps: Double = 0.0,
    val targetRollerVelocityRps: Double = 0.0,
    val isDeployed: Boolean = false
)

/**
 * Immutable representation of the feeder/transfer system and its beam break sensor.
 */
data class FeederState(
    val velocityRps: Double = 0.0,
    val targetVelocityRps: Double = 0.0,
    val gamePieceDetected: Boolean = false
)

/**
 * Immutable representation of the fast-climber elevator system.
 */
data class ClimberState(
    val extensionMeters: Double = 0.0,
    val targetExtensionMeters: Double = 0.0,
    val currentAmps: Double = 0.0,
    val targetVoltage: Double = 0.0
)

/**
 * Immutable representation of the floor rollers.
 */
data class FloorState(
    val velocityRps: Double = 0.0,
    val targetVelocityRps: Double = 0.0,
    val currentAmps: Double = 0.0
)

