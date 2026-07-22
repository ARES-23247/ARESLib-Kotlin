package com.areslib.subsystem

import com.areslib.Store
import com.areslib.action.RobotAction
import com.areslib.math.geometry.Pose2d
import com.areslib.state.RobotState

/**
 * Class implementation for Drive Subsystem.
 *
 * Robotics framework control component.
 */
class DriveSubsystem(private val store: Store) : DrivetrainSubsystem {
    var maxSpeedMps: Double = 3.5

    val xVelocity: Double 
        get() = store.state.drive.xVelocityMetersPerSecond

    val yVelocity: Double 
        get() = store.state.drive.yVelocityMetersPerSecond

    val odometryX: Double 
        get() = store.state.drive.odometryX

    val odometryY: Double 
        get() = store.state.drive.odometryY

    val odometryHeading: Double 
        get() = store.state.drive.odometryHeading

    val odometryPose: Pose2d 
        get() = store.state.drive.poseEstimator.estimatedPose

    val angularVelocity: Double
        get() = store.state.drive.angularVelocityRadiansPerSecond

    /**
     * joystickDrive declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun joystickDrive(x: Double, y: Double, rot: Double, isFieldCentric: Boolean = true, isXLock: Boolean = false) {
        store.dispatch(RobotAction.JoystickDriveIntent(
            targetXVelocity = x,
            targetYVelocity = y,
            targetAngularVelocity = rot,
            isFieldCentric = isFieldCentric,
            isXLock = isXLock,
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        ))
    }

    /**
     * setChassisSpeeds declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun setChassisSpeeds(vx: Double, vy: Double, omega: Double) {
        joystickDrive(
            x = vx / maxSpeedMps,
            y = vy / maxSpeedMps,
            rot = omega / maxSpeedMps,
            isFieldCentric = false
        )
    }

    /**
     * getEstimatedPose declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getEstimatedPose(): Pose2d {
        return odometryPose
    }

    /**
     * readSensors declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun readSensors(store: Store, timestampMs: Long) {}
    /**
     * writeOutputs declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun writeOutputs(state: RobotState, scale: Double) {}
}
