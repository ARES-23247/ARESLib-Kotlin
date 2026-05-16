package com.areslib.frc

import com.areslib.state.RobotState
import edu.wpi.first.util.struct.Struct
import java.nio.ByteBuffer

/**
 * Native AdvantageScope Serializer (WPILib Struct).
 * Serializes the immutable RobotState without reflection or KAPT.
 */
class RobotStateStruct : Struct<RobotState> {
    override fun getTypeClass(): Class<RobotState> = RobotState::class.java
    override fun getTypeString(): String = "struct:RobotState"
    override fun getSize(): Int = java.lang.Double.BYTES * 7 + java.lang.Long.BYTES * 2 + 1 // 1 bool

    override fun getSchema(): String {
        return "double drive_xVel; double drive_yVel; double drive_omega; double odometry_x; double odometry_y; double odometry_heading; double elev_height; bool intake_active; int64 vision_time; double vision_x; double vision_y; bool vision_hasTarget; int64 timestamp;"
    }

    override fun pack(bb: ByteBuffer, value: RobotState) {
        bb.putDouble(value.drive.xVelocityMetersPerSecond)
        bb.putDouble(value.drive.yVelocityMetersPerSecond)
        bb.putDouble(value.drive.angularVelocityRadiansPerSecond)
        bb.putDouble(value.drive.odometryX)
        bb.putDouble(value.drive.odometryY)
        bb.putDouble(value.drive.odometryHeading)
        bb.putDouble(value.superstructure.elevatorHeightMeters)
        bb.put((if (value.superstructure.intakeActive) 1 else 0).toByte())
        bb.putLong(value.vision.lastTargetTimestampMs)
        bb.putDouble(value.vision.targetX)
        bb.putDouble(value.vision.targetY)
        bb.put((if (value.vision.hasTarget) 1 else 0).toByte())
        bb.putLong(value.timestampMs)
    }

    override fun unpack(bb: ByteBuffer): RobotState {
        // Unpacking is generally not needed on the robot for AdvantageScope logging,
        // but required by the interface. We throw to enforce one-way data flow.
        throw UnsupportedOperationException("RobotState is immutable and should only be packed for logging.")
    }
}
