package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.pathing.Path

/**
 * A highly simplified, student-facing modular facade for a Swerve drivetrain subsystem.
 *
 * This class abstracts away raw hardware module kinematics, orientation transformations, and
 * active closed-loop feedback controls by dispatching clean intents to the underlying Redux [Store].
 *
 * It features:
 * - Direct robot-relative strafing and translation.
 * - Auto-translating field-centric driving.
 * - Active orientation stabilization (heading lock) utilizing a proportional IMU feedback controller.
 *
 * @param store The shared Redux [Store] managing global robot state.
 */
class SwerveDriveFacade(private val store: Store) {
    
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

    private val headingPID = com.areslib.control.PIDController(4.5, 0.0, 0.25).apply {
        enableContinuousInput(-Math.PI, Math.PI)
        setOutputLimits(-2.0, 2.0)
    }

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
            isFieldCentric = false,
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        ))
    }

    /**
     * Executes field-relative swerve drivetrain movement effort.
     *
     * Coordinates are translated automatically using the current EKF-estimated heading
     * to keep controls consistent regardless of which way the robot is facing.
     *
     * @param vx Field-centric X-axis velocity effort scaled between [-1.0, 1.0].
     * @param vy Field-centric Y-axis velocity effort scaled between [-1.0, 1.0].
     * @param omega Angular rotational velocity effort scaled between [-1.0, 1.0].
     * @param useHeadingLock Enables active IMU closed-loop heading lock to stabilize the robot's orientation.
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
                // If driver is actively rotating, release lock target and disable heading hold mode
                if (store.state.drive.headingLockTargetRadians != null || store.state.drive.driveMode != com.areslib.state.DriveMode.TELEOP) {
                    store.dispatch(RobotAction.SetHeadingLockTarget(null))
                    store.dispatch(RobotAction.SetDriveMode(com.areslib.state.DriveMode.TELEOP))
                }
            } else {
                val target = store.state.drive.headingLockTargetRadians
                if (target == null) {
                    // Set lock target to current heading if first frame
                    store.dispatch(RobotAction.SetHeadingLockTarget(headingRad))
                    store.dispatch(RobotAction.SetDriveMode(com.areslib.state.DriveMode.HEADING_HOLD))
                } else {
                    // Calculate closed-loop PID feedback to hold orientation
                    finalOmega = headingPID.calculate(headingRad, target, 0.02)
                }
            }
        } else {
            if (store.state.drive.headingLockTargetRadians != null) {
                store.dispatch(RobotAction.SetHeadingLockTarget(null))
            }
        }

        robotRelativeDrive(robotVx, robotVy, finalOmega)
    }

    /**
     * Commands all swerve modules to lock into an "X" configuration (orthogonal angles)
     * with exactly 0.0 speed. This resists pushes from opponent robots.
     */
    fun brake() {
        store.dispatch(RobotAction.SetDriveMode(com.areslib.state.DriveMode.X_BRAKE))
    }

    /**
     * Configures the active target autonomous navigation [Path] for the robot.
     *
     * This registers the path's starting points and signals the underlying swerve trajectory follower
     * to initiate path tracking.
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
