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
import com.areslib.control.tuning.PIDFCoefficients
import com.areslib.control.tuning.SimpleFeedforwardCoeffs
import com.areslib.reducer.rootReducer

/**
 * An out-of-the-box standard FTC Mecanum Robot base class.
 *
 * This provides standard bindings for a 4-wheel mecanum drive and a generic superstructure.
 * Handles WPILib-style background subsystem execution via an internal [Store].
 */
open class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    hardwareMap: com.qualcomm.robotcore.hardware.HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    rlName: String = "rl",
    rrName: String = "rr",
    flDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
    frDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    rlDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
    rrDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    pinpointName: String? = null,
    limelightName: String? = null,
    imuName: String? = "imu",
    localTelemetry: org.firstinspires.ftc.robotcore.external.Telemetry? = null,
    
    // Drivetrain Tunable Constants
    val trackWidthMeters: Double = 0.45,
    val wheelBaseMeters: Double = 0.45,
    val headingGains: PIDFCoefficients = PIDFCoefficients(4.5, 0.0, 0.25),
    val headingDeadzoneDeg: Double = 0.5,
    val driveFeedforward: SimpleFeedforwardCoeffs = SimpleFeedforwardCoeffs(0.0),
    val driveSlewRateLimit: Double? = null,
    val pathTranslationGains: PIDFCoefficients = PIDFCoefficients(2.0, 0.0, 0.02),
    val pathRotationGains: PIDFCoefficients = PIDFCoefficients(2.5, 0.0, 0.05),
    
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
    pinpointIsCcwPositive: Boolean = true,
    
    // Motor Tunable Constants
    val motorGains: PIDFCoefficients? = null,
    val ticksPerMeter: Double = 2000.0,
    
    // Vision Filtering Constants
    visionStdDevs: com.areslib.math.geometry.Vector3 = com.areslib.math.geometry.Vector3(0.05, 0.05, 0.1),
    visionFilterConfig: com.areslib.hardware.vision.VisionFilterConfig = com.areslib.hardware.vision.VisionFilterConfig.ftcDefaults(),
    reducer: (com.areslib.state.RobotState, com.areslib.action.RobotAction) -> com.areslib.state.RobotState = ::rootReducer
) : FtcBaseRobot(
    hardwareMap = hardwareMap,
    pinpointName = pinpointName,
    limelightName = limelightName,
    imuName = imuName,
    localTelemetry = localTelemetry,
    odomQx = odomQx,
    odomQy = odomQy,
    odomQtheta = odomQtheta,
    pinpointXOffsetMm = pinpointXOffsetMm,
    pinpointYOffsetMm = pinpointYOffsetMm,
    pinpointEncoderResolution = pinpointEncoderResolution,
    pinpointXDirection = pinpointXDirection,
    pinpointYDirection = pinpointYDirection,
    pinpointIsCcwPositive = pinpointIsCcwPositive,
    visionStdDevs = visionStdDevs,
    visionFilterConfig = visionFilterConfig,
    reducer = reducer
) {

    // Pre-allocated telemetry buffers (Zero-GC compliance)
    private val EMPTY_SYSID_DATA = DoubleArray(0)

    // Subsystem Facades
    val drive = DriveSubsystem(store)
    val mecanumDrive = MecanumDriveFacade(store, headingGains, headingDeadzoneDeg)

    private val visionAlignController = VisionAlignController()

    private val tuningManager = com.areslib.tuning.TuningManager(
        store = store,
        telemetry = telemetryManager.dataLoggingTelemetry,
        saveFile = java.io.File(if (isAndroid) "/sdcard/FIRST/ares_tuning.json" else "ares_tuning.json")
    )

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
        initialKs = driveFeedforward.kS,
        initialSlewRateLimit = driveSlewRateLimit,
        motorKp = motorGains?.kP,
        motorKi = motorGains?.kI,
        motorKd = motorGains?.kD,
        motorKf = motorGains?.kF
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
    var customSysIdVelocityProvider: (() -> Double)? = null
    
    private var lastLocalTelemetryUpdateMs = 0L
    private var lastCommandProcessed = ""
    private var activeCalibration = "NONE"
    private var calibrationStartTimeMs = 0L

    override fun updateHardwareInputs() {
        com.areslib.hardware.HardwareRegistry.refreshAll()
        
        tuningManager.update()
        
        val command = telemetryManager.nt4.getString("SysId/Command", "")
        if (command != lastCommandProcessed) {
            lastCommandProcessed = command
            activeCalibration = "NONE"
            sysIdManager.stop()
            
            when {
                command == "STOP" -> {
                    mecanumIO.setMotorPowers(0.0, 0.0, 0.0, 0.0)
                    lastTuning = null // Force restoration of configured offsets
                }
                command == "START_PINPOINT_SPIN" -> {
                    activeCalibration = "PINPOINT_SPIN"
                    calibrationStartTimeMs = com.areslib.util.RobotClock.currentTimeMillis()
                    pinpointIO?.setOffsets(0.0, 0.0) // Zero out offsets for absolute calibration
                }
                command == "START_TRACK_WIDTH_SPIN" -> {
                    activeCalibration = "TRACK_WIDTH_SPIN"
                    calibrationStartTimeMs = com.areslib.util.RobotClock.currentTimeMillis()
                }
                command == "START_VISION_CALIBRATION" -> {
                    activeCalibration = "VISION_CALIBRATION"
                    calibrationStartTimeMs = com.areslib.util.RobotClock.currentTimeMillis()
                }
                command == "START_LINEAR_DRIVE" -> {
                    activeCalibration = "LINEAR_DRIVE"
                    calibrationStartTimeMs = com.areslib.util.RobotClock.currentTimeMillis()
                }
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
            mecanumIO.kS = currentTuning.driveFeedforward.kS
            mecanumIO.slewRateLimit = currentTuning.driveSlewRateLimit
            
            val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
            val maxAngularSpeed = maxSpeed / kinematics.k
            drive.maxSpeedMps = maxSpeed
            mecanumDrive.maxSpeedMps = maxSpeed
            mecanumDrive.maxAngularSpeedRps = maxAngularSpeed
            
            // If the path follower was already initialized, update its PIDs
            if (wasPathfindRequested || activePathfindTask != null) {
                pathfindFollower.xController.p = currentTuning.pathTranslationGains.kP
                pathfindFollower.xController.i = currentTuning.pathTranslationGains.kI
                pathfindFollower.xController.d = currentTuning.pathTranslationGains.kD
                pathfindFollower.yController.p = currentTuning.pathTranslationGains.kP
                pathfindFollower.yController.i = currentTuning.pathTranslationGains.kI
                pathfindFollower.yController.d = currentTuning.pathTranslationGains.kD
                pathfindFollower.thetaController.p = currentTuning.pathRotationGains.kP
                pathfindFollower.thetaController.i = currentTuning.pathRotationGains.kI
                pathfindFollower.thetaController.d = currentTuning.pathRotationGains.kD
            }
            
            visionTracker.stdDevs = com.areslib.math.geometry.Vector3(
                currentTuning.visionStdDevsX,
                currentTuning.visionStdDevsY,
                currentTuning.visionStdDevsHeading
            )

            // Dynamic EKF process noise updates
            com.areslib.math.estimation.PoseEstimator.qX = currentTuning.odomQx
            com.areslib.math.estimation.PoseEstimator.qY = currentTuning.odomQy
            com.areslib.math.estimation.PoseEstimator.qTheta = currentTuning.odomQtheta

            // Dynamic Pinpoint offset and resolution updates
            pinpointIO?.let { p ->
                p.setOffsets(currentTuning.pinpointXOffsetMm, currentTuning.pinpointYOffsetMm)
                p.setEncoderResolution(currentTuning.pinpointEncoderResolution)
            }
            
            lastTuning = currentTuning
        }

        val pose = store.state.drive.poseEstimator.estimatedPose
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        
        if (sysIdManager.isActive()) {
            if (sysIdManager.activeMechanism == SysIdMechanism.LINEAR || sysIdManager.activeMechanism == SysIdMechanism.ANGULAR) {
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
                // Non-drive mechanism safety check (primarily time limit)
                if (!sysIdManager.checkSafety(pose.x, pose.y, pose.heading.radians, timestamp)) {
                    sysIdManager.stop()
                }
            }
        } else if (activeCalibration != "NONE") {
            val elapsedSec = (timestamp - calibrationStartTimeMs) / 1000.0
            val timeoutSec = if (activeCalibration == "LINEAR_DRIVE") 3.0 else 5.0
            
            if (elapsedSec > timeoutSec) {
                activeCalibration = "NONE"
                telemetryManager.nt4.putString("SysId/Command", "STOP")
                mecanumIO.setMotorPowers(0.0, 0.0, 0.0, 0.0)
                lastTuning = null // Force restoration of configured offsets
            } else {
                when (activeCalibration) {
                    "PINPOINT_SPIN", "TRACK_WIDTH_SPIN" -> {
                        mecanumIO.setMotorPowers(-0.25, 0.25, -0.25, 0.25)
                    }
                    "VISION_CALIBRATION" -> {
                        mecanumIO.setMotorPowers(0.0, 0.0, 0.0, 0.0)
                    }
                    "LINEAR_DRIVE" -> {
                        mecanumIO.setMotorPowers(0.25, 0.25, 0.25, 0.25)
                    }
                }
            }
        } else {
            mecanumIO.drive(store.state.drive, kinematics, batteryVoltage, dtSeconds)
        }
    }

    override fun publishRobotTelemetry(timestamp: Long) {
        if (timestamp - lastLocalTelemetryUpdateMs >= 100L) {
            telemetryManager.customDriverStationText["Motor Powers"] = String.format("FL:%.2f | FR:%.2f | RL:%.2f | RR:%.2f",
                mecanumIO.flIO.power * mecanumIO.flIO.powerScale,
                mecanumIO.frIO.power * mecanumIO.frIO.powerScale,
                mecanumIO.rlIO.power * mecanumIO.rlIO.powerScale,
                mecanumIO.rrIO.power * mecanumIO.rrIO.powerScale
            )

            val currentStr = if (powerManager.floodgate != null) {
                String.format("%.1f A (Physical)", powerManager.floodgate.current)
            } else {
                String.format("%.1f A (Estimated)", powerManager.currentAmps)
            }
            telemetryManager.customDriverStationText["Current Draw"] = currentStr
            lastLocalTelemetryUpdateMs = timestamp
        }

        // Detailed hardware analytics logging
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fl", mecanumIO.flIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fr", mecanumIO.frIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("rl", mecanumIO.rlIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("rr", mecanumIO.rrIO)

        // SysId/Calibration Telemetry Streaming
        val dataLogging = telemetryManager.dataLoggingTelemetry
        if (sysIdManager.isActive()) {
            dataLogging.putString("SysId/Status", sysIdManager.activeRoutine.name)
            val pose = store.state.drive.poseEstimator.estimatedPose
            val position = when (sysIdManager.activeMechanism) {
                SysIdMechanism.LINEAR -> {
                    val dx = pose.x - sysIdManager.startX
                    val dy = pose.y - sysIdManager.startY
                    kotlin.math.sqrt(dx * dx + dy * dy)
                }
                SysIdMechanism.ANGULAR -> sysIdManager.accumulatedHeadingChange
                SysIdMechanism.FLYWHEEL -> sysIdManager.accumulatedPosition
            }
            
            val velocity = when (sysIdManager.activeMechanism) {
                SysIdMechanism.LINEAR -> store.state.drive.xVelocityMetersPerSecond
                SysIdMechanism.ANGULAR -> store.state.drive.angularVelocityRadiansPerSecond
                SysIdMechanism.FLYWHEEL -> customSysIdVelocityProvider?.invoke() ?: 0.0
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
        } else if (activeCalibration != "NONE") {
            dataLogging.putString("SysId/Status", activeCalibration)
            val pose = store.state.drive.poseEstimator.estimatedPose
            when (activeCalibration) {
                "PINPOINT_SPIN" -> {
                    dataLogging.putDoubleArray(
                        "SysId/Data",
                        doubleArrayOf(
                            timestamp.toDouble(),
                            pose.x,
                            pose.y,
                            pose.heading.radians,
                            0.0
                        )
                    )
                }
                "TRACK_WIDTH_SPIN" -> {
                    val currentTicks = store.state.tuning.ticksPerMeter
                    val ticks = if (currentTicks > 0.0) currentTicks else ticksPerMeter
                    
                    val flPosMeters = mecanumIO.flIO.position / ticks
                    val frPosMeters = mecanumIO.frIO.position / ticks
                    val rlPosMeters = mecanumIO.rlIO.position / ticks
                    val rrPosMeters = mecanumIO.rrIO.position / ticks
                    val imuHeading = imuIO?.let {
                        val inputs = com.areslib.hardware.sensor.ImuInputs()
                        it.updateInputs(inputs)
                        inputs.headingRadians
                    } ?: 0.0
                    
                    dataLogging.putDoubleArray(
                        "SysId/Data",
                        doubleArrayOf(
                            timestamp.toDouble(),
                            flPosMeters,
                            frPosMeters,
                            rlPosMeters,
                            rrPosMeters,
                            imuHeading
                        )
                    )
                }
                "VISION_CALIBRATION" -> {
                    val lastLL = visionTracker.lastLimelightPose
                    val tagX = lastLL?.x ?: 0.0
                    val tagY = lastLL?.y ?: 0.0
                    val tagHeading = lastLL?.heading?.radians ?: 0.0
                    dataLogging.putDoubleArray(
                        "SysId/Data",
                        doubleArrayOf(
                            timestamp.toDouble(),
                            tagX,
                            tagY,
                            tagHeading,
                            0.0
                        )
                    )
                }
                "LINEAR_DRIVE" -> {
                    val currentTicks = store.state.tuning.ticksPerMeter
                    val ticks = if (currentTicks > 0.0) currentTicks else ticksPerMeter
                    
                    val flPosMeters = mecanumIO.flIO.position / ticks
                    val frPosMeters = mecanumIO.frIO.position / ticks
                    val rlPosMeters = mecanumIO.rlIO.position / ticks
                    val rrPosMeters = mecanumIO.rrIO.position / ticks
                    val avgDisplacement = (flPosMeters + frPosMeters + rlPosMeters + rrPosMeters) / 4.0
                    
                    dataLogging.putDoubleArray(
                        "SysId/Data",
                        doubleArrayOf(
                            timestamp.toDouble(),
                            avgDisplacement,
                            0.0,
                            0.0,
                            0.0
                        )
                    )
                }
                else -> {
                    dataLogging.putDoubleArray("SysId/Data", EMPTY_SYSID_DATA)
                }
            }
        } else {
            dataLogging.putString("SysId/Status", "NONE")
            dataLogging.putDoubleArray("SysId/Data", EMPTY_SYSID_DATA)
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
    
    /** 
     * Declarative AutoBuilder facade. 
     * Automatically configured to use this robot's physical drivetrain and pathfind PID coefficients.
     */
    val autoBuilder by lazy { com.areslib.pathing.AutoBuilder().configureFollower(pathfindFollower) }
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
                telemetryManager.customDriverStationText["Error"] = "Waypoint '$name' not found!"
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

    private var fallbackX = 0.0
    private var fallbackY = 0.0
    private var lastFlPos = 0.0
    private var lastFrPos = 0.0
    private var lastRlPos = 0.0
    private var lastRrPos = 0.0
    private var isFallbackInitialized = false

    override fun getFallbackPoseUpdate(timestampMs: Long): RobotAction.PoseUpdate {
        // Read current encoder positions (ticks)
        val flPos = mecanumIO.flIO.position
        val frPos = mecanumIO.frIO.position
        val rlPos = mecanumIO.rlIO.position
        val rrPos = mecanumIO.rrIO.position

        val currentTicks = store.state.tuning.ticksPerMeter
        val ticks = if (currentTicks > 0.0) currentTicks else ticksPerMeter

        // Convert ticks to meters
        val flMeters = flPos / ticks
        val frMeters = frPos / ticks
        val rlMeters = rlPos / ticks
        val rrMeters = rrPos / ticks

        val heading = imuIO?.let {
            val inputs = com.areslib.hardware.sensor.ImuInputs()
            it.updateInputs(inputs)
            inputs.headingRadians
        } ?: 0.0

        if (!isFallbackInitialized) {
            lastFlPos = flMeters
            lastFrPos = frMeters
            lastRlPos = rlMeters
            lastRrPos = rrMeters
            isFallbackInitialized = true
            
            // On first init, return pose at origin (0, 0)
            return RobotAction.PoseUpdate(
                xMeters = 0.0,
                yMeters = 0.0,
                headingRadians = heading,
                timestampMs = timestampMs
            )
        }

        // Encoder deltas
        val dFl = flMeters - lastFlPos
        val dFr = frMeters - lastFrPos
        val dRl = rlMeters - lastRlPos
        val dRr = rrMeters - lastRrPos

        // Save for next update
        lastFlPos = flMeters
        lastFrPos = frMeters
        lastRlPos = rlMeters
        lastRrPos = rrMeters

        // Mecanum forward kinematics: robot-centric dx and dy
        val dx = (dFl + dFr + dRl + dRr) / 4.0
        val dy = (-dFl + dFr + dRl - dRr) / 4.0

        // Field-centric rotation
        val cos = kotlin.math.cos(heading)
        val sin = kotlin.math.sin(heading)

        val deltaFieldX = dx * cos - dy * sin
        val deltaFieldY = dx * sin + dy * cos

        fallbackX += deltaFieldX
        fallbackY += deltaFieldY

        return RobotAction.PoseUpdate(
            xMeters = fallbackX,
            yMeters = fallbackY,
            headingRadians = heading,
            timestampMs = timestampMs
        )
    }

    override fun close() {
        super.close()
        // Ensure proxy restarts for wireless tuning after the OpMode ends
        if (isAndroid) {
            LimelightProxyAutoStart.start()
        }
    }
}

