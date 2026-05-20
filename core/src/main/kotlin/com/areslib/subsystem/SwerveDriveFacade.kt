package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.pathing.Path

class SwerveDriveFacade(private val store: Store) {
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

    private var headingLockTarget: Double? = null
    private val kPHeadingLock = 4.0 // heading lock proportional constant

    /**
     * Standard robot-relative drive.
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
     * Field-relative drive using estimated gyro heading.
     */
    fun fieldRelativeDrive(vx: Double, vy: Double, omega: Double, useHeadingLock: Boolean = false) {
        val headingRad = pose.heading.radians
        val cos = kotlin.math.cos(headingRad)
        val sin = kotlin.math.sin(headingRad)

        // Translate field-relative to robot-relative velocities
        val robotVx = vx * cos + vy * sin
        val robotVy = -vx * sin + vy * cos

        var finalOmega = omega
        if (useHeadingLock) {
            if (kotlin.math.abs(omega) > 0.05) {
                // If driver is actively rotating, release lock target
                headingLockTarget = null
            } else {
                // Set lock target to current heading if first frame
                val target = headingLockTarget ?: headingRad.also { headingLockTarget = it }
                // Calculate simple proportional feedback to hold orientation
                var error = target - headingRad
                while (error > Math.PI) error -= 2 * Math.PI
                while (error < -Math.PI) error += 2 * Math.PI
                finalOmega = error * kPHeadingLock
            }
        } else {
            headingLockTarget = null
        }

        robotRelativeDrive(robotVx, robotVy, finalOmega)
    }

    /**
     * Sets the active target path for the swerve trajectory follower.
     */
    fun followPath(path: Path) {
        store.dispatch(RobotAction.PoseUpdate(
            xMeters = path.points.firstOrNull()?.pose?.x ?: pose.x,
            yMeters = path.points.firstOrNull()?.pose?.y ?: pose.y,
            headingRadians = path.points.firstOrNull()?.pose?.heading?.radians ?: pose.heading.radians,
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        ))
    }
}
