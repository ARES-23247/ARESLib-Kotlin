package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.ftc.drivetrain.MecanumHardwareIO
import com.areslib.kinematics.MecanumKinematics
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import com.areslib.math.geometry.ChassisSpeeds
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.areslib.telemetry.logDriveMotor
import com.areslib.subsystem.DriveSubsystem
import com.areslib.subsystem.MecanumDriveFacade
import com.areslib.action.RobotAction
import com.areslib.control.drivetrain.VisionAlignController
import com.areslib.control.assist.SysIdManager
import com.areslib.control.assist.SysIdMechanism
import com.areslib.control.assist.SysIdRoutine
import com.areslib.ftc.telemetry.LimelightProxyAutoStart

/**
 * Concrete Mecanum Drive robot facade.
 * Implements wheel kinematics conversions, battery sag motor compensation,
 * and publishes mecanum-specific motor currents and powers.
 */
class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    rlName: String = "rl",
    rrName: String = "rr",
    pinpointName: String? = "pinpoint",
    limelightName: String? = "limelight",
    localTelemetry: Telemetry? = null,
    flDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    frDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
    rlDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    rrDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
    
    // Drivetrain Tunable Constants
    val trackWidthMeters: Double = 0.45,
    val wheelBaseMeters: Double = 0.45,
    val headingKp: Double = 4.5,
    val headingKi: Double = 0.0,
    val headingKd: Double = 0.25,
    val headingDeadzoneDeg: Double = 0.5,
    val driveKs: Double = 0.0,
    val driveSlewRateLimit: Double? = null,
    val pathTranslationKp: Double = 2.0,
    val pathTranslationKi: Double = 0.0,
    val pathTranslationKd: Double = 0.02,
    val pathRotationKp: Double = 2.5,
    val pathRotationKi: Double = 0.0,
    val pathRotationKd: Double = 0.05,
    
    // EKF Process Noise Constants
    odomQx: Double = 0.01,
    odomQy: Double = 0.01,
    odomQtheta: Double = 0.01,
    
    // Pinpoint physical parameters
    pinpointXOffsetMm: Double = 0.0,
    pinpointYOffsetMm: Double = 0.0,
    pinpointEncoderResolution: Double? = null,
    pinpointXDirection: com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection = com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection.FORWARD,
    pinpointYDirection: com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection = com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection.FORWARD,
    
    // Motor Tunable Constants
    val motorKp: Double? = null,
    val motorKi: Double? = null,
    val motorKd: Double? = null,
    val motorKf: Double? = null,
    
    // Vision Filtering Constants
    visionStdDevs: com.areslib.math.geometry.Vector3 = com.areslib.math.geometry.Vector3(0.05, 0.05, 0.1),
    visionFilterConfig: com.areslib.hardware.vision.VisionFilterConfig = com.areslib.hardware.vision.VisionFilterConfig.ftcDefaults()
) : FtcBaseRobot(
    hardwareMap = hardwareMap,
    pinpointName = pinpointName,
    limelightName = limelightName,
    localTelemetry = localTelemetry,
    odomQx = odomQx,
    odomQy = odomQy,
    odomQtheta = odomQtheta,
    pinpointXOffsetMm = pinpointXOffsetMm,
    pinpointYOffsetMm = pinpointYOffsetMm,
    pinpointEncoderResolution = pinpointEncoderResolution,
    pinpointXDirection = pinpointXDirection,
    pinpointYDirection = pinpointYDirection,
    visionStdDevs = visionStdDevs,
    visionFilterConfig = visionFilterConfig
) {

    // Subsystem Facades
    val drive = DriveSubsystem(store)
    val mecanumDrive = MecanumDriveFacade(store, headingKp, headingKi, headingKd, headingDeadzoneDeg)

    private val visionAlignController = VisionAlignController()

    // 0. Superstructure Hardware (Optional)
    // Removed intake and shooter as they belong in TeamCode

    init {
        if (isAndroid) {
            LimelightProxyAutoStart.start()
        }
    }

    // 1. Physical Hardware IO & Kinematics Controllers
    val mecanumIO = MecanumHardwareIO(
        hardwareMap = hardwareMap,
        flName = flName,
        frName = frName,
        rlName = rlName,
        rrName = rrName,
        flDirection = flDirection,
        frDirection = frDirection,
        rlDirection = rlDirection,
        rrDirection = rrDirection,
        initialKs = driveKs,
        initialSlewRateLimit = driveSlewRateLimit,
        motorKp = motorKp,
        motorKi = motorKi,
        motorKd = motorKd,
        motorKf = motorKf
    )

    private var kinematics = MecanumKinematics(trackWidthMeters = trackWidthMeters, wheelBaseMeters = wheelBaseMeters)
    private var lastTuning: com.areslib.state.TuningState? = null

    init {
        // Core initializations
        val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
        val maxAngularSpeed = maxSpeed / kinematics.k
        
        drive.maxSpeedMps = maxSpeed
        mecanumDrive.maxSpeedMps = maxSpeed
        
        mecanumDrive.maxAngularSpeedRps = maxAngularSpeed
    }

    val sysIdManager = SysIdManager()
    
    private var lastLocalTelemetryUpdateMs = 0L
    private var lastCommandProcessed = ""

    override fun updateHardwareInputs() {
        com.areslib.hardware.HardwareRegistry.refreshAll()
        
        val command = telemetryManager.nt4.getString("SysId/Command", "")
        if (command != lastCommandProcessed) {
            lastCommandProcessed = command
            when {
                command == "STOP" -> sysIdManager.stop()
                command.startsWith("START_") -> {
                    val parts = command.removePrefix("START_").split("_")
                    if (parts.size >= 2) {
                        val mechStr = parts[0]
                        val routineStr = command.removePrefix("START_${mechStr}_")
                        
                        val mechanism = try {
                            SysIdMechanism.valueOf(mechStr)
                        } catch (e: Exception) {
                            SysIdMechanism.LINEAR
                        }
                        
                        val routine = try {
                            SysIdRoutine.valueOf(routineStr)
                        } catch (e: Exception) {
                            SysIdRoutine.NONE
                        }
                        
                        val pose = store.state.drive.poseEstimator.estimatedPose
                        sysIdManager.start(
                            mechanism = mechanism,
                            routine = routine,
                            timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                            x = pose.x,
                            y = pose.y,
                            heading = pose.heading.radians
                        )
                    }
                }
            }
        }
    }

    override fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double) {
        val currentTuning = store.state.tuning
        if (currentTuning !== lastTuning) {
            kinematics = MecanumKinematics(currentTuning.trackWidthMeters, currentTuning.wheelBaseMeters)
            mecanumIO.kS = currentTuning.driveKs
            mecanumIO.slewRateLimit = currentTuning.driveSlewRateLimit
            
            val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
            val maxAngularSpeed = maxSpeed / kinematics.k
            drive.maxSpeedMps = maxSpeed
            mecanumDrive.maxSpeedMps = maxSpeed
            mecanumDrive.maxAngularSpeedRps = maxAngularSpeed
            
            // If the path follower was already initialized, update its PIDs
            if (wasPathfindRequested || activePathfindTask != null) {
                pathfindFollower.xController.p = currentTuning.pathTranslationKp
                pathfindFollower.xController.i = currentTuning.pathTranslationKi
                pathfindFollower.xController.d = currentTuning.pathTranslationKd
                pathfindFollower.yController.p = currentTuning.pathTranslationKp
                pathfindFollower.yController.i = currentTuning.pathTranslationKi
                pathfindFollower.yController.d = currentTuning.pathTranslationKd
                pathfindFollower.thetaController.p = currentTuning.pathRotationKp
                pathfindFollower.thetaController.i = currentTuning.pathRotationKi
                pathfindFollower.thetaController.d = currentTuning.pathRotationKd
            }
            
            visionTracker.stdDevs = com.areslib.math.geometry.Vector3(
                currentTuning.visionStdDevsX,
                currentTuning.visionStdDevsY,
                currentTuning.visionStdDevsHeading
            )
            
            lastTuning = currentTuning
        }

        val pose = store.state.drive.poseEstimator.estimatedPose
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        
        if (sysIdManager.isActive()) {
            if (!sysIdManager.checkSafety(pose.x, pose.y, pose.heading.radians, timestamp)) {
                sysIdManager.stop()
                mecanumIO.setMotorPowers(0.0, 0.0, 0.0, 0.0)
            } else {
                val velocity = if (sysIdManager.activeMechanism == SysIdMechanism.LINEAR) {
                    store.state.drive.xVelocityMetersPerSecond
                } else {
                    store.state.drive.angularVelocityRadiansPerSecond
                }
                
                val voltage = sysIdManager.update(timestamp, velocity)
                val power = (voltage / batteryVoltage).coerceIn(-1.0, 1.0)
                
                if (sysIdManager.activeMechanism == SysIdMechanism.LINEAR) {
                    mecanumIO.setMotorPowers(power, power, power, power)
                } else {
                    mecanumIO.setMotorPowers(-power, power, -power, power)
                }
            }
        } else {
            mecanumIO.drive(store.state.drive, kinematics, batteryVoltage, dtSeconds)
        }
    }

    override fun publishRobotTelemetry(timestamp: Long) {
        if (timestamp - lastLocalTelemetryUpdateMs >= 100L) {
            localTelemetry?.let { t ->
                t.addData("Motor Powers", String.format("FL:%.2f | FR:%.2f | RL:%.2f | RR:%.2f",
                    mecanumIO.flIO.power * mecanumIO.flIO.powerScale,
                    mecanumIO.frIO.power * mecanumIO.frIO.powerScale,
                    mecanumIO.rlIO.power * mecanumIO.rlIO.powerScale,
                    mecanumIO.rrIO.power * mecanumIO.rrIO.powerScale
                ))

                val currentStr = if (powerManager.floodgate != null) {
                    String.format("%.1f A (Physical)", powerManager.floodgate.current)
                } else {
                    String.format("%.1f A (Estimated)", powerManager.currentAmps)
                }
                t.addData("Current Draw", currentStr)
            }
            lastLocalTelemetryUpdateMs = timestamp
        }

        // Detailed hardware analytics logging
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fl", mecanumIO.flIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fr", mecanumIO.frIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("rl", mecanumIO.rlIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("rr", mecanumIO.rrIO)

        // SysId Telemetry Streaming
        val dataLogging = telemetryManager.dataLoggingTelemetry
        dataLogging.putString("SysId/Status", sysIdManager.activeRoutine.name)
        if (sysIdManager.isActive()) {
            val pose = store.state.drive.poseEstimator.estimatedPose
            val position = if (sysIdManager.activeMechanism == SysIdMechanism.LINEAR) {
                val dx = pose.x - sysIdManager.startX
                val dy = pose.y - sysIdManager.startY
                kotlin.math.sqrt(dx * dx + dy * dy)
            } else {
                sysIdManager.accumulatedHeadingChange
            }
            
            val velocity = if (sysIdManager.activeMechanism == SysIdMechanism.LINEAR) {
                store.state.drive.xVelocityMetersPerSecond
            } else {
                store.state.drive.angularVelocityRadiansPerSecond
            }
            
            dataLogging.putDoubleArray(
                "SysId/Data",
                doubleArrayOf(
                    timestamp.toDouble(),
                    sysIdManager.currentVoltage,
                    position,
                    velocity,
                    sysIdManager.calculatedAcceleration
                )
            )
        } else {
            dataLogging.putDoubleArray("SysId/Data", doubleArrayOf())
        }
    }

    override fun safeHardware() {
        com.areslib.hardware.HardwareRegistry.safeAll()
        stopAll()
    }

    /**
     * Beginner API: Standard drive method (defaults to field-centric)
     */
    fun drive(x: Double, y: Double, rotation: Double) {
        driveFieldCentric(x, y, rotation)
    }

    /**
     * Beginner API: Drive the robot field-centric
     */
    fun driveFieldCentric(x: Double, y: Double, rotation: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(x, y, rotation, isFieldCentric = true))
    }

    /**
     * Beginner API: Drive the robot from the robot's local frame of reference
     */
    fun driveRobotCentric(x: Double, y: Double, rotation: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(x, y, rotation, isFieldCentric = false))
    }

    /**
     * Beginner API: Auto-align the robot to a specific AprilTag ID
     */
    fun alignToTag(tagId: Int) {
        val intent = visionAlignController.calculate(store.state, tagId, true)
        if (intent != null) {
            store.dispatch(intent)
        }
    }

    private var activePathfindTask: com.areslib.sequencer.PathfindToPoseTask? = null
    private var pathfindStartMs = 0L
    private val pathfindFollower by lazy { com.areslib.pathing.HolonomicPathFollower(drive) }
    private var wasPathfindRequested = false

    /**
     * Drives the robot to a target pose on the field, automatically generating
     * and following a path around static obstacles.
     */
    @kotlin.jvm.JvmOverloads
    fun driveToPose(targetPose: Pose2d, isRequested: Boolean, mirrorForAlliance: Boolean = true) {
        val now = com.areslib.util.RobotClock.currentTimeMillis()
        val task = activePathfindTask
        val elapsed = if (task != null) now - pathfindStartMs else 0L

        when {
            isRequested && !wasPathfindRequested -> {
                val config = com.areslib.state.RobotFieldManager.activeConfig
                val costmap = com.areslib.pathing.Costmap.fromFieldConfig(config)
                
                activePathfindTask = com.areslib.sequencer.PathfindToPoseTask(
                    targetPose = targetPose,
                    follower = pathfindFollower,
                    costmap = costmap,
                    maxVelocityMps = mecanumIO.maxWheelSpeedMetersPerSecond * 0.85,
                    maxAccelerationMps2 = 3.0,
                    mirrorForAlliance = mirrorForAlliance
                )
                
                pathfindStartMs = now
                val initActions = activePathfindTask!!.initialize(store.state)
                initActions.forEach { store.dispatch(it) }
                wasPathfindRequested = true
            }
            isRequested && wasPathfindRequested && task != null && task.isCompleted(store.state, elapsed) -> {
                val endActions = task.end(store.state, interrupted = false)
                endActions.forEach { store.dispatch(it) }
                activePathfindTask = null
            }
            isRequested && wasPathfindRequested && task != null -> {
                val execActions = task.execute(store.state, elapsed)
                execActions.forEach { store.dispatch(it) }
            }
            !isRequested && wasPathfindRequested -> {
                if (task != null) {
                    val endActions = task.end(store.state, interrupted = true)
                    endActions.forEach { store.dispatch(it) }
                }
                pathfindFollower.stop()
                activePathfindTask = null
                wasPathfindRequested = false
            }
        }
    }

    /**
     * Drives the robot to a named field waypoint loaded from field_waypoints.json,
     * automatically pathfinding around static obstacles.
     */
    @kotlin.jvm.JvmOverloads
    fun driveToWaypoint(name: String, isRequested: Boolean, mirrorForAlliance: Boolean = true) {
        val wp = com.areslib.pathing.FieldWaypointLoader.getWaypoint(name)
        if (wp != null) {
            driveToPose(wp.toPose(), isRequested, mirrorForAlliance)
        } else {
            if (isRequested) {
                localTelemetry?.addData("Error", "Waypoint '$name' not found!")
            }
            driveToPose(Pose2d(0.0, 0.0, Rotation2d(0.0)), false, false)
        }
    }

    /**
     * Beginner API: Stop all mechanisms
     */
    fun stopAll() {
        com.areslib.hardware.HardwareRegistry.safeAll()
    }

    override fun close() {
        super.close()
        // Ensure proxy restarts for wireless tuning after the OpMode ends
        if (isAndroid) {
            LimelightProxyAutoStart.start()
        }
    }
}

