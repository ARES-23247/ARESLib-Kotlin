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
        val timestampMs: Long
    ) : RobotAction()
    
    data class VisionUpdate(
        val hasTarget: Boolean,
        val targetX: Double,
        val targetY: Double,
        val timestampMs: Long
    ) : RobotAction()

    data class PoseUpdate(
        val xMeters: Double,
        val yMeters: Double,
        val headingRadians: Double,
        val timestampMs: Long
    ) : RobotAction()

    // Human Intent
    data class JoystickDriveIntent(
        val targetXVelocity: Double,
        val targetYVelocity: Double,
        val targetAngularVelocity: Double
    ) : RobotAction()
}
