package com.areslib.ftc

import com.areslib.action.RobotAction
import com.areslib.control.assist.SysIdManager
import com.areslib.control.drivetrain.VisionAlignController
import com.areslib.control.tuning.PIDFCoefficients
import com.areslib.control.tuning.SimpleFeedforwardCoeffs
import com.areslib.ftc.calibration.FtcMecanumCalibrationController
import com.areslib.ftc.drivetrain.MecanumHardwareIO
import com.areslib.ftc.drivetrain.MecanumFallbackOdometry
import com.areslib.ftc.pathing.FtcMecanumPathingController
import com.areslib.ftc.telemetry.LimelightProxyAutoStart
import com.areslib.kinematics.MecanumKinematics
import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Vector3
import com.areslib.pathing.AutoBuilder
import com.areslib.reducer.rootReducer
import com.areslib.state.RobotState
import com.areslib.state.TuningState
import com.areslib.subsystem.DriveSubsystem
import com.areslib.subsystem.MecanumDriveFacade
import com.areslib.telemetry.logDriveMotor
import com.areslib.tuning.TuningManager
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry
import java.io.File

/**
 * An out-of-the-box standard FTC Mecanum Robot base class.
 *
 * Provides standard bindings for a 4-wheel mecanum drive and generic superstructure execution.
 * Handles WPILib-style background subsystem execution via an internal Redux Store.
 */
open class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    rlName: String = "rl",
    rrName: String = "rr",
    flDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE,
    frDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    rlDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.REVERSE,
    rrDirection: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD,
    pinpointName: String? = null,
    limelightName: String? = null,
    imuName: String? = "imu",
    localTelemetry: Telemetry? = null,

    // Drivetrain Tunable Constants
    val trackWidthMeters: Double = 0.45,
    val wheelBaseMeters: Double = 0.45,
    val headingGains: PIDFCoefficients = PIDFCoefficients(4.5, 0.0, 0.25),
    val headingDeadzoneDeg: Double = 0.5,
    val driveFeedforward: SimpleFeedforwardCoeffs = SimpleFeedforwardCoeffs(0.05, 0.12, 0.01),
    val useClosedLoopVelocity: Boolean = false,
    val driveSlewRateLimit: Double? = null,
    val pathTranslationGains: PIDFCoefficients = PIDFCoefficients(2.0, 0.0, 0.2),
    val pathRotationGains: PIDFCoefficients = PIDFCoefficients(2.5, 0.0, 0.2),

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
    val motorGains: PIDFCoefficients? = PIDFCoefficients(0.4, 0.1, 0.01, 0.0),
    val ticksPerMeter: Double = 2000.0,

    // Vision Filtering Constants
    visionStdDevs: Vector3 = Vector3(0.05, 0.05, 0.1),
    visionFilterConfig: com.areslib.hardware.vision.VisionFilterConfig = com.areslib.hardware.vision.VisionFilterConfig.ftcDefaults(),
    reducer: (RobotState, RobotAction) -> RobotState = ::rootReducer
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

    // Subsystem Facades
    val drive = DriveSubsystem(store)
    val mecanumDrive = MecanumDriveFacade(store, headingGains, headingDeadzoneDeg)

    private val visionAlignController = VisionAlignController()

    private val tuningManager = TuningManager(
        store = store,
        telemetry = telemetryManager.dataLoggingTelemetry,
        saveFile = File(if (isAndroid) "/sdcard/FIRST/ares_tuning.json" else "ares_tuning.json")
    )

    init {
        if (isAndroid) {
            LimelightProxyAutoStart.start()
        }
    }

    // Physical Hardware IO & Kinematics Controllers
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
        useClosedLoopVelocity = useClosedLoopVelocity,
        ticksPerMeter = ticksPerMeter,
        initialSlewRateLimit = driveSlewRateLimit,
        motorKp = motorGains?.kP,
        motorKi = motorGains?.kI,
        motorKd = motorGains?.kD,
        motorKf = motorGains?.kF
    )

    private var kinematics = MecanumKinematics(trackWidthMeters = trackWidthMeters, wheelBaseMeters = wheelBaseMeters)
    private var lastTuning: TuningState? = null

    init {
        val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
        val maxAngularSpeed = maxSpeed / kinematics.k

        drive.maxSpeedMps = maxSpeed
        mecanumDrive.maxSpeedMps = maxSpeed
        mecanumDrive.maxAngularSpeedRps = maxAngularSpeed
    }

    // Delegated Specialized Controllers
    private val calibrationController = FtcMecanumCalibrationController()
    private val fallbackOdometry = MecanumFallbackOdometry()
    private val pathingController by lazy { FtcMecanumPathingController(drive) }

    // Backward-compatible delegates for calibration & SysId
    val sysIdManager: SysIdManager get() = calibrationController.sysIdManager
    var customSysIdVelocityProvider: (() -> Double)?
        get() = calibrationController.customSysIdVelocityProvider
        set(value) { calibrationController.customSysIdVelocityProvider = value }

    // Backward-compatible delegates for Pathfinding & Waypoints
    val autoBuilder: AutoBuilder get() = pathingController.autoBuilder

    private var lastLocalTelemetryUpdateMs = 0L

    override fun updateHardwareInputs() {
        com.areslib.hardware.HardwareRegistry.refreshAll()
        tuningManager.update()

        calibrationController.updateHardwareInputs(
            store = store,
            telemetryManager = telemetryManager,
            mecanumIO = mecanumIO,
            pinpointIO = pinpointIO,
            onResetTuning = { lastTuning = null }
        )
    }

    override fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double) {
        val currentTuning = store.state.tuning
        if (currentTuning !== lastTuning) {
            kinematics = MecanumKinematics(currentTuning.trackWidthMeters, currentTuning.wheelBaseMeters)
            mecanumIO.kS = currentTuning.driveFeedforward.kS
            mecanumIO.slewRateLimit = currentTuning.driveSlewRateLimit
            mecanumIO.ticksPerMeter = currentTuning.ticksPerMeter
            if (currentTuning.driveFeedforward.kV > 1e-4) {
                mecanumIO.maxWheelSpeedMetersPerSecond = 1.0 / currentTuning.driveFeedforward.kV
            }
            val gains = currentTuning.motorGains
            if (gains != null) {
                mecanumIO.updateMotorGains(gains.kP, gains.kI, gains.kD)
            }

            val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
            val maxAngularSpeed = maxSpeed / kinematics.k
            drive.maxSpeedMps = maxSpeed
            mecanumDrive.maxSpeedMps = maxSpeed
            mecanumDrive.maxAngularSpeedRps = maxAngularSpeed

            pathingController.updateTuning(currentTuning)

            visionTracker.stdDevs = Vector3(
                currentTuning.visionStdDevsX,
                currentTuning.visionStdDevsY,
                currentTuning.visionStdDevsHeading
            )

            com.areslib.math.estimation.PoseEstimator.qX = currentTuning.odomQx
            com.areslib.math.estimation.PoseEstimator.qY = currentTuning.odomQy
            com.areslib.math.estimation.PoseEstimator.qTheta = currentTuning.odomQtheta

            pinpointIO?.let { p ->
                p.setOffsets(currentTuning.pinpointXOffsetMm, currentTuning.pinpointYOffsetMm)
                p.setEncoderResolution(currentTuning.pinpointEncoderResolution)
            }

            lastTuning = currentTuning
        }

        val isCalibrationHandlingDrive = calibrationController.updateSubsystems(
            store = store,
            batteryVoltage = batteryVoltage,
            mecanumIO = mecanumIO,
            telemetryManager = telemetryManager,
            onResetTuning = { lastTuning = null }
        )

        if (!isCalibrationHandlingDrive) {
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

        // Hardware analytics logging
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fl", mecanumIO.flIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fr", mecanumIO.frIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("rl", mecanumIO.rlIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("rr", mecanumIO.rrIO)

        calibrationController.publishRobotTelemetry(
            timestamp = timestamp,
            store = store,
            telemetryManager = telemetryManager,
            mecanumIO = mecanumIO,
            imuIO = imuIO,
            visionTracker = visionTracker,
            ticksPerMeterSetting = ticksPerMeter,
            defaultTicksPerMeter = 2000.0
        )
    }

    override fun safeHardware() {
        com.areslib.hardware.HardwareRegistry.safeAll()
        stopAll()
    }

    /** Beginner API: Standard drive method (defaults to field-centric) */
    fun drive(x: Double, y: Double, rotation: Double) {
        driveFieldCentric(x, y, rotation)
    }

    /** Beginner API: Drive the robot field-centric */
    fun driveFieldCentric(x: Double, y: Double, rotation: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(x, y, rotation, isFieldCentric = true))
    }

    /** Beginner API: Drive the robot from the robot's local frame of reference */
    fun driveRobotCentric(x: Double, y: Double, rotation: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(x, y, rotation, isFieldCentric = false))
    }

    /** Beginner API: Auto-align the robot to a specific AprilTag ID */
    fun alignToTag(tagId: Int) {
        val intent = visionAlignController.calculate(store.state, tagId, true)
        if (intent != null) {
            store.dispatch(intent)
        }
    }

    /**
     * Drives the robot to a target pose on the field, automatically generating
     * and following a path around static obstacles.
     */
    @kotlin.jvm.JvmOverloads
    fun driveToPose(targetPose: Pose2d, isRequested: Boolean, mirrorForAlliance: Boolean = true) {
        pathingController.driveToPose(store, mecanumIO, targetPose, isRequested, mirrorForAlliance)
    }

    /**
     * Drives the robot to a named field waypoint loaded from field_waypoints.json,
     * automatically pathfinding around static obstacles.
     */
    @kotlin.jvm.JvmOverloads
    fun driveToWaypoint(name: String, isRequested: Boolean, mirrorForAlliance: Boolean = true) {
        pathingController.driveToWaypoint(store, mecanumIO, telemetryManager, name, isRequested, mirrorForAlliance)
    }

    /** Beginner API: Stop all mechanisms */
    fun stopAll() {
        com.areslib.hardware.HardwareRegistry.safeAll()
    }

    override fun getFallbackPoseUpdate(timestampMs: Long): RobotAction.PoseUpdate {
        val heading = imuIO?.let {
            val inputs = com.areslib.hardware.sensor.ImuInputs()
            it.updateInputs(inputs)
            inputs.headingRadians
        } ?: 0.0

        return fallbackOdometry.getFallbackPoseUpdate(
            timestampMs = timestampMs,
            flPosTicks = mecanumIO.flIO.position,
            frPosTicks = mecanumIO.frIO.position,
            rlPosTicks = mecanumIO.rlIO.position,
            rrPosTicks = mecanumIO.rrIO.position,
            ticksPerMeterSetting = store.state.tuning.ticksPerMeter,
            defaultTicksPerMeter = ticksPerMeter,
            headingRadians = heading
        )
    }

    override fun close() {
        super.close()
        if (isAndroid) {
            LimelightProxyAutoStart.start()
        }
    }
}
