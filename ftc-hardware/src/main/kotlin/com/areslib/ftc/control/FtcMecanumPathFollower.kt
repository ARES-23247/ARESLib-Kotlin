package com.areslib.ftc.control

import com.areslib.ftc.FtcMecanumRobot
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.pathing.Path
import com.areslib.pathing.PathPoint

/**
 * A reusable, unified autonomous path follower for FTC Mecanum Robots.
 * Encapsulates a [HolonomicDriveController] with configurable PID gains
 * to steer the robot along generated spline paths based on EKF odometry.
 */
class FtcMecanumPathFollower @kotlin.jvm.JvmOverloads constructor(
    val robot: FtcMecanumRobot,
    val xController: PIDController = PIDController(p = 1.5, i = 0.0, d = 0.15),
    val yController: PIDController = PIDController(p = 1.5, i = 0.0, d = 0.15),
    val thetaController: PIDController = PIDController(p = 2.5, i = 0.0, d = 0.05).apply {
        enableContinuousInput(-Math.PI, Math.PI)
    }
) {
    /** Underlying holonomic controller fusing feedback and path tangents */
    val driveController = HolonomicDriveController(xController, yController, thetaController)

    /**
     * Updates the drivetrain commands to track the target state of a spline path.
     * Calculates the required field-relative steering and feeds it to the robot facade.
     *
     * @param targetState The desired target position, heading, and velocity sample from the path.
     * @param dtSeconds Elapsed time since the last controller update in seconds.
     */
    fun update(targetState: PathPoint, dtSeconds: Double) {

        val currentPose = robot.drive.odometryPose
        
        // Calculate the required chassis speed vectors
        val chassisSpeeds = driveController.calculate(
            currentPose = currentPose,
            targetPose = targetState.pose,
            targetVelocityMps = targetState.velocityMps,
            targetHeading = targetState.pose.heading,
            dtSeconds = dtSeconds,
            pathTangentRadians = targetState.tangentRadians
        )

        // Normalize velocities against the robot's physical maximum speed capability
        val maxSpeed = robot.mecanumIO.maxWheelSpeedMetersPerSecond
        robot.drive.joystickDrive(
            x = chassisSpeeds.vxMetersPerSecond / maxSpeed,
            y = chassisSpeeds.vyMetersPerSecond / maxSpeed,
            rot = chassisSpeeds.omegaRadiansPerSecond / maxSpeed,
            isFieldCentric = false
        )
    }

    /**
     * Helper to instantly stop the robot's movement.
     */
    fun stop() {
        robot.drive.joystickDrive(0.0, 0.0, 0.0, false)
    }
}
