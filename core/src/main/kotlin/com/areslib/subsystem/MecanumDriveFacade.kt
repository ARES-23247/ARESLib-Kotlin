package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.pathing.Path

/**
 * A highly simplified, student-facing modular facade for a Mecanum drivetrain subsystem.
 *
 * This class abstracts away raw hardware controls and state propagation by dispatching
 * clean actions to the underlying Redux [Store]. This preserves log-replay capabilities
 * and guarantees exact teleop/simulation determinism.
 *
 * Example usage in student teleop loops:
 * ```kotlin
 * robot.mecanumDrive.fieldRelativeDrive(
 *     vx = gamepad1.left_stick_x.toDouble(),
 *     vy = -gamepad1.left_stick_y.toDouble(),
 *     omega = -gamepad1.right_stick_x.toDouble()
 * )
 * ```
 *
 * @param store The shared Redux [Store] managing global robot state.
 */
class MecanumDriveFacade(private val store: Store) {
    
    /**
     * The current estimated longitudinal (X-axis) velocity of the robot on the field in meters per second.
     */
    val xVelocity: Double
        get() = store.state.drive.xVelocityMetersPerSecond

    /**
     * The current estimated lateral (Y-axis) velocity of the robot on the field in meters per second.
     */
    val yVelocity: Double
        get() = store.state.drive.yVelocityMetersPerSecond

    /**
     * The current estimated angular velocity of the robot in radians per second.
     */
    val angularVelocity: Double
        get() = store.state.drive.angularVelocityRadiansPerSecond

    /**
     * The current 2D spatial pose of the robot ([Pose2d]) on the coordinate field, estimated via EKF.
     */
    val pose: Pose2d
        get() = store.state.drive.poseEstimator.estimatedPose

    /**
     * The raw X coordinate of the odometry system computer in meters.
     */
    val odometryX: Double
        get() = store.state.drive.odometryX

    /**
     * The raw Y coordinate of the odometry system computer in meters.
     */
    val odometryY: Double
        get() = store.state.drive.odometryY

    /**
     * The raw heading of the odometry system computer in radians.
     */
    val odometryHeading: Double
        get() = store.state.drive.odometryHeading

    /**
     * Executes robot-relative drivetrain movement effort.
     *
     * Coordinates are specified relative to the robot's local frame where:
     * - +X is longitudinal forward velocity effort.
     * - +Y is lateral strafe-left velocity effort.
     * - +Omega is counter-clockwise rotational velocity effort.
     *
     * @param vx Longitudinal velocity effort scaled between [-1.0, 1.0].
     * @param vy Lateral strafe velocity effort scaled between [-1.0, 1.0].
     * @param omega Angular rotational velocity effort scaled between [-1.0, 1.0].
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
     * Executes field-relative holonomic drivetrain movement effort.
     *
     * Coordinates are translated automatically using the current EKF-estimated heading
     * to keep controls consistent regardless of which way the robot is facing.
     * - +X moves the robot away from the driver (field-forward).
     * - +Y moves the robot to the driver's left (field-left).
     * - +Omega rotates the robot counter-clockwise.
     *
     * @param vx Field-centric X-axis velocity effort scaled between [-1.0, 1.0].
     * @param vy Field-centric Y-axis velocity effort scaled between [-1.0, 1.0].
     * @param omega Angular rotational velocity effort scaled between [-1.0, 1.0].
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
     * Configures the active target autonomous navigation [Path] for the robot.
     *
     * This registers the path's starting points and signals the underlying drivetrain control
     * loops to initiate path following tracking.
     *
     * @param path The target [Path] to follow.
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
