package com.areslib.sim

import com.areslib.math.ChassisSpeeds
import com.areslib.state.RobotState
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.networktables.StructPublisher
import com.areslib.frc.RobotStateStruct
import edu.wpi.first.wpilibj.DataLogManager

object TelemetryPublisher {
    private val ntInst = NetworkTableInstance.getDefault()
    private val statePublisher: StructPublisher<RobotState>
    private val targetPosePublisher = ntInst.getDoubleArrayTopic("AdvantageKit/RealOutputs/ARES/TargetPose").publish()
    private val estimatedPosePublisher = ntInst.getDoubleArrayTopic("AdvantageKit/RealOutputs/ARES/EstimatedPose").publish()
    private val gamePiecesPublisher = ntInst.getDoubleArrayTopic("AdvantageKit/RealOutputs/ARES/GamePieces").publish()

    // --- AdvantageKit-level Swerve Module Telemetry ---
    private val moduleSpeedsTargetPub = ntInst.getDoubleArrayTopic("AdvantageKit/RealOutputs/Swerve/ModuleSpeedsTarget").publish()
    private val moduleAnglesTargetPub = ntInst.getDoubleArrayTopic("AdvantageKit/RealOutputs/Swerve/ModuleAnglesTarget").publish()
    private val moduleSpeedsActualPub = ntInst.getDoubleArrayTopic("AdvantageKit/RealOutputs/Swerve/ModuleSpeedsActual").publish()
    private val moduleAnglesActualPub = ntInst.getDoubleArrayTopic("AdvantageKit/RealOutputs/Swerve/ModuleAnglesActual").publish()
    
    // Chassis Speeds
    private val chassisVxPub = ntInst.getDoubleTopic("AdvantageKit/RealOutputs/Swerve/ChassisSpeeds/vx").publish()
    private val chassisVyPub = ntInst.getDoubleTopic("AdvantageKit/RealOutputs/Swerve/ChassisSpeeds/vy").publish()
    private val chassisOmegaPub = ntInst.getDoubleTopic("AdvantageKit/RealOutputs/Swerve/ChassisSpeeds/omega").publish()

    // Drive mode
    private val fieldCentricPub = ntInst.getBooleanTopic("AdvantageKit/RealOutputs/Drive/FieldCentric").publish()
    private val teleopModePub = ntInst.getBooleanTopic("AdvantageKit/RealOutputs/Drive/TeleopMode").publish()

    // Superstructure telemetry
    private val flywheelRPMPub = ntInst.getDoubleTopic("AdvantageKit/RealOutputs/Superstructure/FlywheelRPM").publish()
    private val flywheelTargetRPMPub = ntInst.getDoubleTopic("AdvantageKit/RealOutputs/Superstructure/FlywheelTargetRPM").publish()
    private val superstructureModePub = ntInst.getStringTopic("AdvantageKit/RealOutputs/Superstructure/Mode").publish()
    private val intakeActivePub = ntInst.getBooleanTopic("AdvantageKit/RealOutputs/Superstructure/IntakeActive").publish()
    private val flywheelActivePub = ntInst.getBooleanTopic("AdvantageKit/RealOutputs/Superstructure/FlywheelActive").publish()
    private val transferActivePub = ntInst.getBooleanTopic("AdvantageKit/RealOutputs/Superstructure/TransferActive").publish()
    private val inventoryCountPub = ntInst.getIntegerTopic("AdvantageKit/RealOutputs/Superstructure/InventoryCount").publish()

    init {
        // Start DataLogManager for offline .wpilog generation
        DataLogManager.start()

        // Configure NT4 for live streaming
        ntInst.startServer()
        
        // Register the custom struct so NT4 knows how to serialize it
        statePublisher = ntInst.getStructTopic("AdvantageKit/RealOutputs/ARES/RobotState", RobotStateStruct()).publish()
    }

    /**
     * Publishes the current state to NT4 and DataLog.
     */
    fun publish(state: RobotState) {
        statePublisher.set(state)
    }

    /**
     * Publishes the target trajectory pose for AdvantageScope "ghost" rendering.
     * AdvantageScope 2D/3D pose arrays expect [x, y, rotation_radians].
     */
    fun publishTargetPose(pose: com.areslib.math.Pose2d) {
        targetPosePublisher.set(doubleArrayOf(pose.x, pose.y, pose.heading.radians))
    }

    /**
     * Publishes the estimated pose from the Kalman Filter (EKF) for AdvantageScope rendering.
     */
    fun publishEstimatedPose(pose: com.areslib.math.Pose2d) {
        estimatedPosePublisher.set(doubleArrayOf(pose.x, pose.y, pose.heading.radians))
    }

    /**
     * Publishes the locations of game pieces on the field.
     */
    fun publishGamePieces(gamePieces: DoubleArray) {
        gamePiecesPublisher.set(gamePieces)
    }

    /**
     * Publishes per-module swerve telemetry (AdvantageKit-level).
     * Each array is 4 elements [FL, FR, BL, BR].
     */
    fun publishSwerveModules(
        speedsTarget: DoubleArray, anglesTarget: DoubleArray,
        speedsActual: DoubleArray, anglesActual: DoubleArray
    ) {
        moduleSpeedsTargetPub.set(speedsTarget)
        moduleAnglesTargetPub.set(anglesTarget)
        moduleSpeedsActualPub.set(speedsActual)
        moduleAnglesActualPub.set(anglesActual)
    }

    /**
     * Publishes commanded chassis speeds.
     */
    fun publishChassisSpeeds(speeds: ChassisSpeeds) {
        chassisVxPub.set(speeds.vxMetersPerSecond)
        chassisVyPub.set(speeds.vyMetersPerSecond)
        chassisOmegaPub.set(speeds.omegaRadiansPerSecond)
    }

    /**
     * Publishes drive mode flags.
     */
    fun publishDriveMode(fieldCentric: Boolean, teleopMode: Boolean) {
        fieldCentricPub.set(fieldCentric)
        teleopModePub.set(teleopMode)
    }

    /**
     * Publishes superstructure state (flywheel RPM, mode, active flags).
     */
    fun publishSuperstructure(state: RobotState) {
        flywheelRPMPub.set(state.superstructure.flywheelRPM)
        flywheelTargetRPMPub.set(state.superstructure.flywheelTargetRPM)
        superstructureModePub.set(state.superstructure.mode.name)
        intakeActivePub.set(state.superstructure.intakeActive)
        flywheelActivePub.set(state.superstructure.flywheelActive)
        transferActivePub.set(state.superstructure.transferActive)
        inventoryCountPub.set(state.superstructure.inventoryCount.toLong())
    }

    /**
     * Shutdown telemetry.
     */
    fun stop() {
        ntInst.stopServer()
        DataLogManager.stop()
    }
}
