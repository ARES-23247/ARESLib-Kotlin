package com.areslib.frc.drivetrain

import com.areslib.state.DriveState
import com.ctre.phoenix6.BaseStatusSignal
import com.ctre.phoenix6.swerve.SwerveDrivetrain

/**
 * Class implementation for Swerve Ctre Drivetrain Reader.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class SwerveCtreDrivetrainReader(private val drivetrain: SwerveDrivetrain<*, *, *>) {
    private val currentDraw1 = drivetrain.getModule(0).driveMotor.supplyCurrent
    private val currentDraw2 = drivetrain.getModule(1).driveMotor.supplyCurrent
    private val currentDraw3 = drivetrain.getModule(2).driveMotor.supplyCurrent
    private val currentDraw4 = drivetrain.getModule(3).driveMotor.supplyCurrent

    private val absEnc1 = (drivetrain.getModule(0).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition
    private val absEnc2 = (drivetrain.getModule(1).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition
    private val absEnc3 = (drivetrain.getModule(2).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition
    private val absEnc4 = (drivetrain.getModule(3).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition

    private val faultHardware = Array(4) { i -> drivetrain.getModule(i).driveMotor.getFault_Hardware() }
    private val faultBrownout = Array(4) { i -> drivetrain.getModule(i).driveMotor.getFault_BridgeBrownout() }
    private val faultTemp = Array(4) { i -> drivetrain.getModule(i).driveMotor.getFault_DeviceTemp() }

    private val pigeon = drivetrain.pigeon2
    private val pitchSignal = pigeon.pitch
    private val rollSignal = pigeon.roll
    private val yawSignal = pigeon.yaw
    private val yawRateSignal = pigeon.angularVelocityZWorld

    init {
        for (i in 0..3) {
            drivetrain.getModule(i).driveMotor.supplyCurrent.setUpdateFrequency(20.0, 0.0)
            (drivetrain.getModule(i).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition.setUpdateFrequency(50.0, 0.0)
            faultHardware[i].setUpdateFrequency(4.0, 0.0)
            faultBrownout[i].setUpdateFrequency(4.0, 0.0)
            faultTemp[i].setUpdateFrequency(4.0, 0.0)
        }
        pitchSignal.setUpdateFrequency(20.0, 0.0)
        rollSignal.setUpdateFrequency(20.0, 0.0)

        drivetrain.registerTelemetry { _ -> }
    }

    fun refresh() {
        BaseStatusSignal.refreshAll(
            currentDraw1, currentDraw2, currentDraw3, currentDraw4,
            absEnc1, absEnc2, absEnc3, absEnc4,
            pitchSignal, rollSignal, yawSignal, yawRateSignal,
            faultHardware[0], faultHardware[1], faultHardware[2], faultHardware[3],
            faultBrownout[0], faultBrownout[1], faultBrownout[2], faultBrownout[3],
            faultTemp[0], faultTemp[1], faultTemp[2], faultTemp[3]
        )
    }

    fun getCurrents(out: DoubleArray) {
        out[0] = currentDraw1.valueAsDouble
        out[1] = currentDraw2.valueAsDouble
        out[2] = currentDraw3.valueAsDouble
        out[3] = currentDraw4.valueAsDouble
    }

    fun getEncoderPositions(out: DoubleArray) {
        out[0] = absEnc1.valueAsDouble
        out[1] = absEnc2.valueAsDouble
        out[2] = absEnc3.valueAsDouble
        out[3] = absEnc4.valueAsDouble
    }

    val pitchDegrees: Double
        get() = pitchSignal.valueAsDouble

    val rollDegrees: Double
        get() = rollSignal.valueAsDouble

    fun getModuleSpeeds(out: DoubleArray) {
        out[0] = drivetrain.state.ModuleStates[0].speedMetersPerSecond
        out[1] = drivetrain.state.ModuleStates[1].speedMetersPerSecond
        out[2] = drivetrain.state.ModuleStates[2].speedMetersPerSecond
        out[3] = drivetrain.state.ModuleStates[3].speedMetersPerSecond
    }

    fun read(): DriveState {
        val driveStateObj = drivetrain.state
        val pose = driveStateObj.Pose

        return DriveState(
            xVelocityMetersPerSecond = driveStateObj.Speeds.vxMetersPerSecond,
            yVelocityMetersPerSecond = driveStateObj.Speeds.vyMetersPerSecond,
            angularVelocityRadiansPerSecond = driveStateObj.Speeds.omegaRadiansPerSecond,
            odometryX = pose.x,
            odometryY = pose.y,
            odometryHeading = pose.rotation.radians
        )
    }
}
