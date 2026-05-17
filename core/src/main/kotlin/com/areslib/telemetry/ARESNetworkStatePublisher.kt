package com.areslib.telemetry

import com.areslib.state.RobotState

/**
 * Serializes and publishes the current RobotState to an ITelemetry interface (e.g. NT4Telemetry).
 */
class ARESNetworkStatePublisher(private val telemetry: ITelemetry) {

    fun publish(state: RobotState) {
        // Publish scalars
        telemetry.putNumber("Drive/Odom_X", state.drive.odometryX)
        telemetry.putNumber("Drive/Odom_Y", state.drive.odometryY)
        telemetry.putNumber("Drive/Odom_Heading", state.drive.odometryHeading)
        telemetry.putNumber("Drive/Velocity_X", state.drive.xVelocityMetersPerSecond)
        telemetry.putNumber("Drive/Velocity_Y", state.drive.yVelocityMetersPerSecond)
        telemetry.putNumber("Drive/Velocity_Omega", state.drive.angularVelocityRadiansPerSecond)

        // Publish Pose2d for AdvantageScope 3D visualization
        if (telemetry is NT4Telemetry) {
            telemetry.putPose2d(
                "AdvantageScope/RobotPose",
                state.drive.odometryX,
                state.drive.odometryY,
                state.drive.odometryHeading
            )
        }
        
        telemetry.update()
    }
}
