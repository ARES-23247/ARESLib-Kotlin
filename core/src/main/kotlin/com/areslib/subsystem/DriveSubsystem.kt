package com.areslib.subsystem

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.state.RobotState

class DriveSubsystem(private val store: Store) {
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

    fun joystickDrive(x: Double, y: Double, rot: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(
            targetXVelocity = x,
            targetYVelocity = y,
            targetAngularVelocity = rot,
            timestampMs = System.currentTimeMillis()
        ))
    }
}
