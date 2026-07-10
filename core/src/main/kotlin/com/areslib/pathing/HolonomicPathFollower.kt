package com.areslib.pathing

import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.subsystem.DrivetrainSubsystem

/**
 * A reusable, platform-independent path follower for holonomic drivetrains (Swerve/Mecanum).
 * Calculates closed-loop velocity commands based on EKF position feedback and target path states.
 */
class HolonomicPathFollower @kotlin.jvm.JvmOverloads constructor(
    val drivetrain: DrivetrainSubsystem,
    val xController: PIDController = PIDController(p = 2.0, i = 0.0, d = 0.02),
    val yController: PIDController = PIDController(p = 2.0, i = 0.0, d = 0.02),
    val thetaController: PIDController = PIDController(p = 2.5, i = 0.0, d = 0.05).apply {
        enableContinuousInput(-Math.PI, Math.PI)
    }
) {
    /** Holonomic controller calculating corrective translational and angular velocities */
    val driveController = HolonomicDriveController(xController, yController, thetaController)

    private var currentPath: Path? = null
    private val triggeredEvents = mutableSetOf<String>()

    /** Callback invoked whenever a PathEvent is crossed */
    var onEventTriggered: ((String) -> Unit)? = null

    /**
     * Initializes tracking for a new path, resetting any previously triggered events.
     */
    fun startPath(path: Path) {
        currentPath = path
        triggeredEvents.clear()
    }

    /**
     * Updates the drivetrain commands to track the target state of a spline path.
     * Calculates the required field-relative steering and feeds it to the drivetrain subsystem.
     *
     * @param targetState The desired target position, heading, and velocity sample from the path.
     * @param dtSeconds Elapsed time since the last controller update in seconds.
     */
    fun update(targetState: PathPoint, dtSeconds: Double) {
        val currentPose = drivetrain.getEstimatedPose()
        
        val path = currentPath
        if (path != null) {
            val currentDist = targetState.distanceMeters
            for (event in path.events) {
                if (currentDist >= event.triggerDistanceMeters && !triggeredEvents.contains(event.eventName)) {
                    triggeredEvents.add(event.eventName)
                    onEventTriggered?.invoke(event.eventName)
                }
            }
        }
        
        val chassisSpeeds = driveController.calculate(
            currentPose = currentPose,
            targetPose = targetState.pose,
            targetVelocityMps = targetState.velocityMps,
            targetHeading = targetState.pose.heading,
            dtSeconds = dtSeconds
        )

        drivetrain.setChassisSpeeds(
            vx = chassisSpeeds.vxMetersPerSecond,
            vy = chassisSpeeds.vyMetersPerSecond,
            omega = chassisSpeeds.omegaRadiansPerSecond
        )
    }

    /**
     * Halts all chassis movement.
     */
    fun stop() {
        drivetrain.setChassisSpeeds(0.0, 0.0, 0.0)
    }
}
