package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.pathing.Path

class MecanumDriveFacade(private val store: Store) {
    val xVelocity: Double
        get() = store.state.drive.xVelocityMetersPerSecond

    val yVelocity: Double
        get() = store.state.drive.yVelocityMetersPerSecond

    val angularVelocity: Double
        get() = store.state.drive.angularVelocityRadiansPerSecond

    val pose: Pose2d
        get() = store.state.drive.poseEstimator.estimatedPose

    val odometryX: Double
        get() = store.state.drive.odometryX

    val odometryY: Double
        get() = store.state.drive.odometryY

    val odometryHeading: Double
        get() = store.state.drive.odometryHeading

    /**
     * Standard drive command using robot-relative controls.
     */
    fun robotRelativeDrive(vx: Double, vy: Double, omega: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(
            targetXVelocity = vx,
            targetYVelocity = vy,
            targetAngularVelocity = omega,
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        ))
    }

    /**
     * Standard drive command using field-relative controls.
     * Translates coordinates automatically based on the current estimated heading.
     */
    fun fieldRelativeDrive(vx: Double, vy: Double, omega: Double) {
        val headingRad = pose.heading.radians
        val cos = kotlin.math.cos(headingRad)
        val sin = kotlin.math.sin(headingRad)
        
        // Field-relative rotation transformation
        val robotVx = vx * cos + vy * sin
        val robotVy = -vx * sin + vy * cos
        
        robotRelativeDrive(robotVx, robotVy, omega)
    }

    /**
     * Sets the active target path for the autonomous follower.
     */
    fun followPath(path: Path) {
        // Dispatches to a path action which reducers can record/handle
        store.dispatch(RobotAction.PoseUpdate(
            xMeters = path.points.firstOrNull()?.pose?.x ?: pose.x,
            yMeters = path.points.firstOrNull()?.pose?.y ?: pose.y,
            headingRadians = path.points.firstOrNull()?.pose?.heading?.radians ?: pose.heading.radians,
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        ))
    }
}
