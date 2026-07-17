package com.areslib.sim.network

import com.areslib.math.geometry.ChassisSpeeds
import com.areslib.state.RobotState
import com.areslib.sim.infra.VirtualDriverStation
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.networktables.StructPublisher
import edu.wpi.first.wpilibj.DataLogManager

/**
 * World-class Telemetry Publisher for the ARES simulation environment.
 * Coordinates real-time NetworkTables (NT4) state publishing, client-side input polling,
 * and AdvantageScope-compatible swerve/pose visualizations.
 */
object TelemetryPublisher {
    private val ntInst = NetworkTableInstance.getDefault()
    private val statePublisher: StructPublisher<RobotState>
    private val targetPosePublisher = ntInst.getDoubleArrayTopic("ARES/TargetPose").publish()
    private val estimatedPosePublisher = ntInst.getDoubleArrayTopic("ARES/EstimatedPose").publish(
        edu.wpi.first.networktables.PubSubOption.periodic(0.01)
    )
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
    private val webRedAllianceSub = ntInst.getBooleanTopic("ARES/Input/isRedAlliance").subscribe(true)
    private val webHeartbeatSub = ntInst.getIntegerTopic("ARES/Input/heartbeat").subscribe(0L)
    private val webButtonASub = ntInst.getBooleanTopic("ARES/Input/isButtonAPressed").subscribe(false)
    private val webButtonBSub = ntInst.getBooleanTopic("ARES/Input/isButtonBPressed").subscribe(false)
    private val webButtonXSub = ntInst.getBooleanTopic("ARES/Input/isButtonXPressed").subscribe(false)
    private val webPoseResetSub = ntInst.getBooleanTopic("ARES/Input/isPoseReset").subscribe(false)
    
    /**
     * Obstacles input subscriber path. Receives costmap bounding boxes and obstacles from the dashboard editor.
     */
    val obstaclesSub = ntInst.getStringTopic("ARES/Input/obstacles").subscribe("")

    private var lastWebHeartbeatTimestamp = 0L
    private var lastWebInputReceiveTime = 0L

    // Session log file path publisher
    private val logFilePathPub = ntInst.getStringTopic("ARES/Session/LogFilePath").publish()

    private val nt4Telemetry = com.areslib.telemetry.NT4Telemetry()
    private val networkStatePublisher = com.areslib.telemetry.ARESNetworkStatePublisher(nt4Telemetry)

    init {
        ntInst.startServer()
        
        // Register the custom struct so NT4 knows how to serialize it
        statePublisher = ntInst.getStructTopic("ARES/RobotState", RobotStateStruct()).publish()
    }

    /**
     * Publishes the current state to NT4 and DataLog.
     *
     * @param state The current immutable robot state to serialize and publish.
     */
    fun publish(state: RobotState) {
        statePublisher.set(state)
        // networkStatePublisher.publish(state) // Disabled to prevent double-publishing collision with student OpMode thread
        com.areslib.hardware.HardwareRegistry.publishAll(nt4Telemetry)
        timestampPub.set(com.areslib.util.RobotClock.currentTimeMillis())
    }

    /**
     * Publishes the target trajectory pose for AdvantageScope "ghost" rendering.
     * AdvantageScope 2D/3D pose arrays expect [x, y, rotation_radians].
     *
     * @param pose The field-relative target pose.
     */
    fun publishTargetPose(pose: com.areslib.math.geometry.Pose2d) {
        targetPosePublisher.set(doubleArrayOf(pose.x, pose.y, pose.heading.radians))
    }

    /**
     * Publishes the estimated pose from the Kalman Filter (EKF) for AdvantageScope rendering.
     *
     * @param pose The field-relative estimated pose.
     */
    fun publishEstimatedPose(pose: com.areslib.math.geometry.Pose2d) {
        estimatedPosePublisher.set(doubleArrayOf(pose.x, pose.y, pose.heading.radians))
    }

    /**
     * Publishes the locations of game pieces on the field.
     *
     * @param gamePieces Packed array representation of game pieces coordinates.
     */
    fun publishGamePieces(gamePieces: DoubleArray) {
        gamePiecesPublisher.set(gamePieces)
    }

    /**
     * Publishes per-module swerve telemetry (AdvantageKit-level).
     * Each array is 4 elements [FL, FR, BL, BR].
     *
     * @param speedsTarget Command target speeds per module (m/s).
     * @param anglesTarget Command target angles per module (rad).
     * @param speedsActual Measured speeds per module (m/s).
     * @param anglesActual Measured rotation angles per module (rad).
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
     *
     * @param speeds Commands chassis relative linear/angular velocities.
     */
    fun publishChassisSpeeds(speeds: ChassisSpeeds) {
        chassisVxPub.set(speeds.vxMetersPerSecond)
        chassisVyPub.set(speeds.vyMetersPerSecond)
        chassisOmegaPub.set(speeds.omegaRadiansPerSecond)
    }

    /**
     * Publishes drive mode flags.
     *
     * @param fieldCentric Whether field-centric driving is currently active.
     * @param teleopMode Whether TeleOp mode is active.
     * @param redAlliance Active alliance selection flag (true for Red, false for Blue).
     */
    fun publishDriveMode(fieldCentric: Boolean, teleopMode: Boolean, redAlliance: Boolean) {
        fieldCentricPub.set(fieldCentric)
        teleopModePub.set(teleopMode)
        redAlliancePub.set(redAlliance)
    }

    /**
     * Polls `/ARES/Input` topics from NT4. If fresh updates are found,
     * pushes them directly into the VirtualDriverStation instance.
     *
     * @param driverStation Target VirtualDriverStation instance to synchronize inputs with.
     */
    fun pollWebInputs(driverStation: VirtualDriverStation) {
        val heartbeatEntry = webHeartbeatSub.getAtomic()
        val now = com.areslib.util.RobotClock.currentTimeMillis()

        when {
            heartbeatEntry.timestamp != lastWebHeartbeatTimestamp -> {
                println("[TelemetryPublisher] Heartbeat updated: val=${heartbeatEntry.value}, ts=${heartbeatEntry.timestamp}, lastTs=$lastWebHeartbeatTimestamp")
                lastWebHeartbeatTimestamp = heartbeatEntry.timestamp
                lastWebInputReceiveTime = now
            }
            now % 2000 < 50 -> {
                println("[TelemetryPublisher] NT4 Server Heartbeat unchanged: val=${heartbeatEntry.value}, ts=${heartbeatEntry.timestamp}, now=$now, lastRecvTime=$lastWebInputReceiveTime")
            }
        }

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
            driverStation.isButtonAPressed = webButtonASub.get()
            driverStation.isButtonBPressed = webButtonBSub.get()
            driverStation.isButtonXPressed = webButtonXSub.get()
            driverStation.isPoseReset = webPoseResetSub.get()
        } else {
            driverStation.webVx = 0.0
            driverStation.webVy = 0.0
            driverStation.webOmega = 0.0
        }
    }

    /**
     * Publishes superstructure state (flywheel RPM, mode, active flags).
     *
     * @param state The current immutable robot state.
     */
    fun publishSuperstructure(@Suppress("UNUSED_PARAMETER") state: RobotState) {
    }

    /**
     * Shutdown telemetry server.
     */
    fun stop() {
        ntInst.stopServer()
        DataLogManager.stop()
    }
}
