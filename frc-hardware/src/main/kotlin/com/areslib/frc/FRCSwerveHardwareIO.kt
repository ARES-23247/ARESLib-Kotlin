package com.areslib.frc

import com.areslib.hardware.drive.SwerveHardwareIO
import com.areslib.state.DriveState
import com.ctre.phoenix6.BaseStatusSignal
import com.ctre.phoenix6.swerve.SwerveDrivetrain
import com.ctre.phoenix6.swerve.SwerveRequest
import edu.wpi.first.math.kinematics.ChassisSpeeds

/**
 * Hardware IO bridge for an FRC Phoenix 6 Swerve Drive.
 * 
 * This class abstracts the highly-optimized CTRE SwerveDrivetrain (which runs internally
 * at 250Hz on the CAN FD bus) into the pure mathematical ARESLib Redux architecture.
 */
class FRCSwerveHardwareIO(private val drivetrain: SwerveDrivetrain<*, *, *>) : SwerveHardwareIO {

    // CTRE Swerve request objects — FieldCentric delegates the heading rotation
    // to CTRE's 250Hz thread (uses Pigeon2 state internally), avoiding stale-heading bugs.
    private val fieldCentricRequest = SwerveRequest.FieldCentric()
    private val robotSpeedsRequest = SwerveRequest.ApplyRobotSpeeds()
    private val scratchSpeeds = ChassisSpeeds()
    


    private val currentDraw1 = drivetrain.getModule(0).driveMotor.supplyCurrent
    private val currentDraw2 = drivetrain.getModule(1).driveMotor.supplyCurrent
    private val currentDraw3 = drivetrain.getModule(2).driveMotor.supplyCurrent
    private val currentDraw4 = drivetrain.getModule(3).driveMotor.supplyCurrent

    private val absEnc1 = (drivetrain.getModule(0).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition
    private val absEnc2 = (drivetrain.getModule(1).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition
    private val absEnc3 = (drivetrain.getModule(2).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition
    private val absEnc4 = (drivetrain.getModule(3).encoder as com.ctre.phoenix6.hardware.CANcoder).absolutePosition

    // Pre-cached fault StatusSignals — avoids allocating new StatusSignal objects in getFaults() hot path
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

        // Register CTRE native telemetry for AdvantageScope and SignalLogger
        drivetrain.registerTelemetry { swerveDriveState ->
            // CTRE automatically streams this to SignalLogger (.wpilog files)
            // and publishes to NetworkTables for AdvantageScope visualization
        }
    }

    override fun refresh() {
        BaseStatusSignal.refreshAll(
            currentDraw1, currentDraw2, currentDraw3, currentDraw4,
            absEnc1, absEnc2, absEnc3, absEnc4,
            pitchSignal, rollSignal, yawSignal, yawRateSignal,
            faultHardware[0], faultHardware[1], faultHardware[2], faultHardware[3],
            faultBrownout[0], faultBrownout[1], faultBrownout[2], faultBrownout[3],
            faultTemp[0], faultTemp[1], faultTemp[2], faultTemp[3]
        )
    }

    override fun safe() {
        write(DriveState())
    }

    override fun getCurrents(out: DoubleArray) {
        out[0] = currentDraw1.valueAsDouble
        out[1] = currentDraw2.valueAsDouble
        out[2] = currentDraw3.valueAsDouble
        out[3] = currentDraw4.valueAsDouble
    }

    override fun getEncoderPositions(out: DoubleArray) {
        out[0] = absEnc1.valueAsDouble
        out[1] = absEnc2.valueAsDouble
        out[2] = absEnc3.valueAsDouble
        out[3] = absEnc4.valueAsDouble
    }

    override val pitchDegrees: Double
        get() = pitchSignal.valueAsDouble

    override val rollDegrees: Double
        get() = rollSignal.valueAsDouble

    override fun getModuleSpeeds(out: DoubleArray) {
        out[0] = drivetrain.state.ModuleStates[0].speedMetersPerSecond
        out[1] = drivetrain.state.ModuleStates[1].speedMetersPerSecond
        out[2] = drivetrain.state.ModuleStates[2].speedMetersPerSecond
        out[3] = drivetrain.state.ModuleStates[3].speedMetersPerSecond
    }

    /**
     * Reads the 250Hz synchronized pose from the CTRE drivetrain and maps it
     * into the Redux DriveState object for the next calculation cycle.
     */
    override fun read(): DriveState {
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

    override val rawGyroYawDegrees: Double
        get() = yawSignal.valueAsDouble

    override val yawRateDegreesPerSecond: Double
        get() = yawRateSignal.valueAsDouble

    /**
     * Applies the macro-level velocities computed by the Redux reducers
     * back to the CTRE SwerveDrivetrain.
     *
     * **Field-centric mode** is delegated to [SwerveRequest.FieldCentric], which
     * performs the field-to-robot rotation internally on the CTRE 250Hz odometry
     * thread using the Pigeon2's latest heading. This eliminates the stale-heading
     * artifacts that occurred when we manually computed cos/sin at 50Hz.
     *
     * **Robot-centric mode** uses [SwerveRequest.ApplyRobotSpeeds] with a
     * pre-allocated [ChassisSpeeds] to maintain zero-GC compliance.
     */
    override fun write(driveState: DriveState) {
        if (driveState.isXLock) {
            drivetrain.setControl(com.ctre.phoenix6.swerve.SwerveRequest.SwerveDriveBrake())
            return
        }
        if (driveState.isFieldCentric) {
            drivetrain.setControl(
                fieldCentricRequest
                    .withVelocityX(driveState.xVelocityMetersPerSecond)
                    .withVelocityY(driveState.yVelocityMetersPerSecond)
                    .withRotationalRate(driveState.angularVelocityRadiansPerSecond)
            )
        } else {
            scratchSpeeds.vxMetersPerSecond = driveState.xVelocityMetersPerSecond
            scratchSpeeds.vyMetersPerSecond = driveState.yVelocityMetersPerSecond
            scratchSpeeds.omegaRadiansPerSecond = driveState.angularVelocityRadiansPerSecond
            drivetrain.setControl(robotSpeedsRequest.withSpeeds(scratchSpeeds))
        }
    }

    /**
     * Feeds AprilTag vision measurements into the CTRE SwerveDrivetrain's internal EKF.
     */
    override fun addVisionMeasurement(pose: com.areslib.math.geometry.Pose2d, timestampSeconds: Double) {
        val wpiPose = edu.wpi.first.math.geometry.Pose2d(
            pose.x,
            pose.y,
            edu.wpi.first.math.geometry.Rotation2d.fromRadians(pose.heading.radians)
        )
        drivetrain.addVisionMeasurement(wpiPose, timestampSeconds)
    }

    /**
     * Resets/seeds the CTRE SwerveDrivetrain internal odometry pose.
     */
    override fun seedPose(pose: com.areslib.math.geometry.Pose2d) {
        val wpiPose = edu.wpi.first.math.geometry.Pose2d(
            pose.x,
            pose.y,
            edu.wpi.first.math.geometry.Rotation2d.fromRadians(pose.heading.radians)
        )
        drivetrain.resetPose(wpiPose)
    }

    override fun getFaults(out: IntArray) {
        if (out.size >= 4) {
            for (i in 0..3) {
                var faultCode = 0
                if (faultHardware[i].value) faultCode = faultCode or 1
                if (faultBrownout[i].value) faultCode = faultCode or 2
                if (faultTemp[i].value) faultCode = faultCode or 4
                out[i] = faultCode
            }
        }
    }

    override val signalLatencyMs: Double
        get() = currentDraw1.timestamp.latency * 1000.0
}

