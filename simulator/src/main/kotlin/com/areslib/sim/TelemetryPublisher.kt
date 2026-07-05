package com.areslib.sim

import com.areslib.math.ChassisSpeeds
import com.areslib.state.RobotState
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.networktables.StructPublisher
import com.areslib.sim.RobotStateStruct
import edu.wpi.first.wpilibj.DataLogManager

object TelemetryPublisher {
    private val ntInst = NetworkTableInstance.getDefault()
    private val statePublisher: StructPublisher<RobotState>
    private val targetPosePublisher = ntInst.getDoubleArrayTopic("ARES/TargetPose").publish()
    private val estimatedPosePublisher = ntInst.getDoubleArrayTopic("ARES/EstimatedPose").publish()
    private val gamePiecesPublisher = ntInst.getDoubleArrayTopic("ARES/GamePieces").publish()
    private val timestampPub = ntInst.getIntegerTopic("TimestampMs").publish()

    // --- AdvantageKit-level Swerve Module Telemetry ---
    private val moduleSpeedsTargetPub = ntInst.getDoubleArrayTopic("Swerve/ModuleSpeedsTarget").publish()
    private val moduleAnglesTargetPub = ntInst.getDoubleArrayTopic("Swerve/ModuleAnglesTarget").publish()
    private val moduleSpeedsActualPub = ntInst.getDoubleArrayTopic("Swerve/ModuleSpeedsActual").publish()
    private val moduleAnglesActualPub = ntInst.getDoubleArrayTopic("Swerve/ModuleAnglesActual").publish()
    
    // Chassis Speeds
    private val chassisVxPub = ntInst.getDoubleTopic("Swerve/ChassisSpeeds/vx").publish()
    private val chassisVyPub = ntInst.getDoubleTopic("Swerve/ChassisSpeeds/vy").publish()
    private val chassisOmegaPub = ntInst.getDoubleTopic("Swerve/ChassisSpeeds/omega").publish()

    // Drive mode
    private val fieldCentricPub = ntInst.getBooleanTopic("Drive/FieldCentric").publish()
    private val teleopModePub = ntInst.getBooleanTopic("Drive/TeleopMode").publish()
    private val redAlliancePub = ntInst.getBooleanTopic("Drive/RedAlliance").publish()

    // --- Web Dashboard Inputs Subscribers ---
    private val webVxSub = ntInst.getDoubleTopic("ARES/Input/vx").subscribe(0.0)
    private val webVySub = ntInst.getDoubleTopic("ARES/Input/vy").subscribe(0.0)
    private val webOmegaSub = ntInst.getDoubleTopic("ARES/Input/omega").subscribe(0.0)
    private val webIntakeSub = ntInst.getBooleanTopic("ARES/Input/isIntaking").subscribe(false)
    private val webFlywheelSub = ntInst.getBooleanTopic("ARES/Input/isFlywheelOn").subscribe(false)
    private val webTransferSub = ntInst.getBooleanTopic("ARES/Input/isTransferring").subscribe(false)
    private val webTeleopSub = ntInst.getBooleanTopic("ARES/Input/isTeleopMode").subscribe(true)
    private val webFieldCentricSub = ntInst.getBooleanTopic("ARES/Input/isFieldCentric").subscribe(false)
    private val webRedAllianceSub = ntInst.getBooleanTopic("ARES/Input/isRedAlliance").subscribe(false)
    private val webHeartbeatSub = ntInst.getIntegerTopic("ARES/Input/heartbeat").subscribe(0L)
    val obstaclesSub = ntInst.getStringTopic("ARES/Input/obstacles").subscribe("")

    private var lastWebHeartbeatTimestamp = 0L
    private var lastWebInputReceiveTime = 0L

    // Superstructure telemetry
    private val flywheelRPMPub = ntInst.getDoubleTopic("Superstructure/FlywheelRPM").publish()
    private val flywheelTargetRPMPub = ntInst.getDoubleTopic("Superstructure/FlywheelTargetRPM").publish()
    private val superstructureModePub = ntInst.getStringTopic("Superstructure/Mode").publish()
    private val intakeActivePub = ntInst.getBooleanTopic("Superstructure/IntakeActive").publish()
    private val flywheelActivePub = ntInst.getBooleanTopic("Superstructure/FlywheelActive").publish()
    private val transferActivePub = ntInst.getBooleanTopic("Superstructure/TransferActive").publish()
    private val inventoryCountPub = ntInst.getIntegerTopic("Superstructure/InventoryCount").publish()

    // Session log file path publisher
    private val logFilePathPub = ntInst.getStringTopic("ARES/Session/LogFilePath").publish()

    init {
        // DataLogManager.start() removed to maintain log format parity with the physical FTC robot.

        // Configure NT4 for live streaming
        ntInst.startServer()
        
        // Register the custom struct so NT4 knows how to serialize it
        statePublisher = ntInst.getStructTopic("ARES/RobotState", RobotStateStruct()).publish()

        // Log path publishing removed since we no longer generate .wpilog files in the simulator
    }

    /**
     * Publishes the current state to NT4 and DataLog.
     */
    fun publish(state: RobotState) {
        statePublisher.set(state)
        timestampPub.set(com.areslib.util.RobotClock.currentTimeMillis())
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
    fun publishDriveMode(fieldCentric: Boolean, teleopMode: Boolean, redAlliance: Boolean) {
        fieldCentricPub.set(fieldCentric)
        teleopModePub.set(teleopMode)
        redAlliancePub.set(redAlliance)
    }

    /**
     * Polls `/ARES/Input` topics from NT4. If fresh updates are found,
     * pushes them directly into the VirtualDriverStation instance.
     */
    fun pollWebInputs(driverStation: VirtualDriverStation) {
        val heartbeatEntry = webHeartbeatSub.getAtomic()
        val now = com.areslib.util.RobotClock.currentTimeMillis()

        // Check if the NetworkTables heartbeat timestamp has changed since our last poll
        if (heartbeatEntry.timestamp != lastWebHeartbeatTimestamp) {
            println("[TelemetryPublisher] Heartbeat updated: val=${heartbeatEntry.value}, ts=${heartbeatEntry.timestamp}, lastTs=$lastWebHeartbeatTimestamp")
            lastWebHeartbeatTimestamp = heartbeatEntry.timestamp
            lastWebInputReceiveTime = now
        } else if (now % 2000 < 50) {
            println("[TelemetryPublisher] NT4 Server Heartbeat unchanged: val=${heartbeatEntry.value}, ts=${heartbeatEntry.timestamp}, now=$now, lastRecvTime=$lastWebInputReceiveTime")
        }

        // Only apply web inputs if we've received an update within the last 1.0 seconds
        val timeDiff = now - lastWebInputReceiveTime
        if (timeDiff < 1000) {
            val vx = webVxSub.get()
            val vy = webVySub.get()
            val omega = webOmegaSub.get()
            if (vx != 0.0 || vy != 0.0 || omega != 0.0) {
                println("[TelemetryPublisher] Applying web inputs: vx=$vx, vy=$vy, omega=$omega (timeDiff=$timeDiff ms)")
            }
            driverStation.webVx = vx
            driverStation.webVy = vy
            driverStation.webOmega = omega

            driverStation.isIntaking = webIntakeSub.get()
            driverStation.isFlywheelOn = webFlywheelSub.get()
            driverStation.isTransferring = webTransferSub.get()
            driverStation.isTeleopMode = webTeleopSub.get()
            driverStation.isFieldCentric = webFieldCentricSub.get()
            driverStation.isRedAlliance = webRedAllianceSub.get()
        } else {
            // Clear web speeds so they don't linger
            driverStation.webVx = 0.0
            driverStation.webVy = 0.0
            driverStation.webOmega = 0.0
        }
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

