package com.areslib.action

/**
 * Represents an immutable intent or hardware update event.
 */
sealed class RobotAction {
    abstract val timestampMs: Long

    // Hardware Updates
    data class DriveHardwareUpdate(
        val xVelocity: Double,
        val yVelocity: Double,
        val angularVelocity: Double,
        val deltaX: Double,
        val deltaY: Double,
        val deltaHeading: Double,
        override val timestampMs: Long,
        val pitchDegrees: Double = 0.0,
        val rollDegrees: Double = 0.0,
        val xAccelerationG: Double = 0.0,
        val yAccelerationG: Double = 0.0,
        val zAccelerationG: Double = 0.0
    ) : RobotAction()
    
    data class VisionUpdate(
        val hasTarget: Boolean,
        val targetX: Double,
        val targetY: Double,
        override val timestampMs: Long
    ) : RobotAction()

    data class VisionMeasurementsReceived(
        val measurements: List<com.areslib.state.VisionMeasurement>,
        override val timestampMs: Long,
        val customVisionStdDevs: com.areslib.math.Vector3? = null
    ) : RobotAction()


    data class PoseUpdate(
        val xMeters: Double,
        val yMeters: Double,
        val headingRadians: Double,
        override val timestampMs: Long,
        val pitchDegrees: Double = 0.0,
        val rollDegrees: Double = 0.0,
        val xAccelerationG: Double = 0.0,
        val yAccelerationG: Double = 0.0,
        val zAccelerationG: Double = 0.0
    ) : RobotAction()

    // Human Intent
    data class JoystickDriveIntent(
        val targetXVelocity: Double,
        val targetYVelocity: Double,
        val targetAngularVelocity: Double,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    // Autonomous Events
    data class PathEventTriggered(
        val eventName: String,
        override val timestampMs: Long
    ) : RobotAction()

    // Superstructure FSM Actions
    data class SetIntakeActive(
        val active: Boolean,
        override val timestampMs: Long
    ) : RobotAction()

    data class SetFlywheelActive(
        val active: Boolean,
        override val timestampMs: Long
    ) : RobotAction()

    data class SetTransferActive(
        val active: Boolean,
        override val timestampMs: Long
    ) : RobotAction()

    data class UpdateFlywheelRPM(
        val rpm: Double,
        override val timestampMs: Long
    ) : RobotAction()

    data class SetInventoryCount(
        val count: Int,
        override val timestampMs: Long
    ) : RobotAction()

    // Obstacle Avoidance Actions
    data class DistanceSensorObservation(
        val sensorId: String,
        val angleOffsetRad: Double,
        val positionOffsetXMeters: Double,
        val positionOffsetYMeters: Double,
        val distanceMeters: Double,
        val maxRangeMeters: Double = 4.0
    )

    data class ObstacleCostmapUpdate(
        val observations: List<DistanceSensorObservation>,
        override val timestampMs: Long
    ) : RobotAction()

    // Path Following and Switching Actions
    data class ChainPaths(
        val paths: List<com.areslib.pathing.Path>,
        val maxVelocityMps: Double = 2.0,
        val maxAccelerationMps2: Double = 1.5,
        override val timestampMs: Long
    ) : RobotAction()

    data class SwitchPath(
        val path: com.areslib.pathing.Path,
        val isDetour: Boolean = false,
        override val timestampMs: Long
    ) : RobotAction()

    data class UpdatePathProgress(
        val distanceProgressMeters: Double,
        override val timestampMs: Long
    ) : RobotAction()

    // FRC Marvin 19 Subsystem Actions
    data class SetFlywheelSpeed(
        val rpm: Double,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    data class SetCowlAngle(
        val degrees: Double,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    data class SetIntakePivot(
        val deployed: Boolean,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    data class SetIntakeRollers(
        val speedRps: Double,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    data class SetFeederSpeed(
        val speedRps: Double,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    data class SetFloorSpeed(
        val speedRps: Double,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    data class SetClimberVoltage(
        val volts: Double,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()

    data class SuperstructureSensorUpdate(
        val flywheelRpm: Double,
        val cowlAngle: Double,
        val intakeAngle: Double,
        val pieceDetected: Boolean,
        val floorVelocityRps: Double = 0.0,
        val climberExtensionMeters: Double = 0.0,
        override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
    ) : RobotAction()
}
