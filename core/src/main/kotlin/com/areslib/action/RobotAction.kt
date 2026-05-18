package com.areslib.action

/**
 * Represents an immutable intent or hardware update event.
 */
sealed class RobotAction {
    // Hardware Updates
    data class DriveHardwareUpdate(
        val xVelocity: Double,
        val yVelocity: Double,
        val angularVelocity: Double,
        val deltaX: Double,
        val deltaY: Double,
        val deltaHeading: Double,
        val timestampMs: Long,
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
        val timestampMs: Long
    ) : RobotAction()

    data class VisionMeasurementsReceived(
        val measurements: List<com.areslib.state.VisionMeasurement>,
        val timestampMs: Long,
        val customVisionStdDevs: com.areslib.math.Vector3? = null
    ) : RobotAction()


    data class PoseUpdate(
        val xMeters: Double,
        val yMeters: Double,
        val headingRadians: Double,
        val timestampMs: Long,
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
        val targetAngularVelocity: Double
    ) : RobotAction()

    // Autonomous Events
    data class PathEventTriggered(
        val eventName: String,
        val timestampMs: Long
    ) : RobotAction()

    // Superstructure FSM Actions
    data class SetIntakeActive(
        val active: Boolean,
        val timestampMs: Long
    ) : RobotAction()

    data class SetFlywheelActive(
        val active: Boolean,
        val timestampMs: Long
    ) : RobotAction()

    data class SetTransferActive(
        val active: Boolean,
        val timestampMs: Long
    ) : RobotAction()

    data class UpdateFlywheelRPM(
        val rpm: Double,
        val timestampMs: Long
    ) : RobotAction()

    data class SetInventoryCount(
        val count: Int,
        val timestampMs: Long
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
        val timestampMs: Long
    ) : RobotAction()

    // Path Following and Switching Actions
    data class ChainPaths(
        val paths: List<com.areslib.pathing.Path>,
        val maxVelocityMps: Double = 2.0,
        val maxAccelerationMps2: Double = 1.5,
        val timestampMs: Long
    ) : RobotAction()

    data class SwitchPath(
        val path: com.areslib.pathing.Path,
        val isDetour: Boolean = false,
        val timestampMs: Long
    ) : RobotAction()

    data class UpdatePathProgress(
        val distanceProgressMeters: Double,
        val timestampMs: Long
    ) : RobotAction()
}
