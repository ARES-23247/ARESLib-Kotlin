package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.areslib.subsystem.AresRobot
import com.areslib.hardware.ftc.vision.FtcLimelightIO
import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.CompositeVisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.kinematics.MecanumKinematics
import com.areslib.math.ChassisSpeeds
import com.areslib.action.RobotAction
import com.areslib.telemetry.GamepadState

import com.areslib.telemetry.NT4Telemetry
import com.areslib.telemetry.DataLoggingTelemetry
import com.areslib.telemetry.ARESNetworkStatePublisher
import com.areslib.control.BrownoutGuard
import com.areslib.control.CurrentBudgetManager
import com.areslib.ftc.hardware.FtcFloodgateCurrentSensor
import com.qualcomm.robotcore.hardware.AnalogInput
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.ftc.hardware.FtcPerformanceManager

/**
 * A hardware facade and coordinator for FTC Mecanum Robots.
 *
 * This class abstracts the motor controller interactions, kinematics, odometry sensors (such as the GoBilda Pinpoint),
 * and vision sensors (such as the Limelight 3A). It implements a unified, low-overhead robot state update loop that:
 * - Integrates high-frequency wheel odometry / Pinpoint measurements into the central Redux state store EKF.
 * - Handles retroactive vision measurements from Limelight with latency compensation and Mahalanobis rejection.
 * - Executes stationary "Kidnapped Robot Recovery" to automatically reseed localization in case of severe drift.
 * - Implements battery-compensated motor voltage scaling, brownout protection, and current budget management.
 * - Publishes telemetry automatically to NetworkTables (NT4) and local Driver Station screens.
 *
 * @property hardwareMap The FTC HardwareMap containing motor and sensor references.
 * @property flName HardwareMap name for the Front-Left motor.
 * @property frName HardwareMap name for the Front-Right motor.
 * @property blName HardwareMap name for the Back-Left motor.
 * @property brName HardwareMap name for the Back-Right motor.
 * @property pinpointName Optional HardwareMap name for the GoBilda Pinpoint driver.
 * @property limelightName Optional HardwareMap name for the Limelight 3A vision sensor.
 * @property localTelemetry Optional telemetry object for local Driver Station output.
 * @property flDirection Direction configuration for the Front-Left motor.
 * @property frDirection Direction configuration for the Front-Right motor.
 * @property blDirection Direction configuration for the Back-Left motor.
 * @property brDirection Direction configuration for the Back-Right motor.
 */
class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    val hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br",
    pinpointName: String? = "pinpoint",
    limelightName: String? = "limelight",
    private val localTelemetry: Telemetry? = null,
    flDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    frDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
    blDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    brDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE
) : AresRobot() {

    init {
        FtcPerformanceManager.initialize(hardwareMap)
        // Intercept and record all dispatched store actions asynchronously
        store.actionListener = { actionLogger.logAction(it) }
        com.areslib.telemetry.RobotWebServer.start()
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotStatusTracker.activeOpMode = "Init"
    }


    // Telemetry & Network Tables State Publisher
    private val nt4 = NT4Telemetry()
    private val dataLoggingTelemetry = DataLoggingTelemetry(nt4)
    private val publisher = ARESNetworkStatePublisher(dataLoggingTelemetry)

    // Asynchronous JSONL recorders for offline deterministic log replay
    private val inputLogger = com.areslib.logging.InputLogger()
    private val actionLogger = com.areslib.action.ActionLogger()

    // 1. Physical Hardware IO & Kinematics Controllers
    val mecanumIO = MecanumHardwareIO(
        hardwareMap, flName, frName, blName, brName,
        flDirection = flDirection,
        frDirection = frDirection,
        blDirection = blDirection,
        brDirection = brDirection
    )
    val pinpointIO: PinpointIO? = try {
        pinpointName?.let { name ->
            val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, name)
            PinpointIO(pinpointDriver)
        }
    } catch (_: Exception) {
        null
    }
    
    private val limelightIO: VisionIO? = try {
        limelightName?.let { namesStr ->
            val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (names.size > 1) {
                val ios = names.map { name ->
                    val limelightDriver = hardwareMap.get(Limelight3A::class.java, name)
                    FtcLimelightIO(limelightDriver)
                }
                CompositeVisionIO(ios)
            } else if (names.size == 1) {
                val limelightDriver = hardwareMap.get(Limelight3A::class.java, names[0])
                FtcLimelightIO(limelightDriver)
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
    private val visionInputs = VisionIOInputs()
    private var lastLimelightPose: com.areslib.math.Pose2d? = null
    private var lastLimelightTimeMs = 0L
    var lastVisionStatus = "OFFLINE"
    private var consecutiveVisionRejections = 0
    var isInInit: Boolean = true
    private var hasInitializedPoseWithVision = false
    
    private var lastUpdateTime = 0L
    private var lastVoltageReadTime = 0L
    private var cachedBatteryVoltage = 12.0
    
    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    /** Brownout protection guard — auto-scales motor power on voltage sag */
    val brownoutGuard = BrownoutGuard.ftcDefaults()

    /** Floodgate V2 current sensor — null if no Floodgate is connected */
    val floodgate: FtcFloodgateCurrentSensor? = try {
        val analogInput = hardwareMap.get(AnalogInput::class.java, "floodgate")
        FtcFloodgateCurrentSensor(analogInput)
    } catch (_: Exception) {
        null // No Floodgate connected — current protection fallback active
    }

    /** Software current budget manager — used as a fallback if no Floodgate sensor is found */
    val currentBudgetManager: CurrentBudgetManager? = if (floodgate == null) {
        CurrentBudgetManager.ftcDefaults().apply {
            register(mecanumIO.flIO)
            register(mecanumIO.frIO)
            register(mecanumIO.blIO)
            register(mecanumIO.brIO)
        }
    } else {
        null
    }

    /**
     * Coordinated frame update for Mecanum Drivetrain.
     *
     * Pass gamepad states to get automatic input logging for replay.
     * If omitted, everything else is still logged — gamepads are optional.
     *
     * @param gamepad1 Optional driver gamepad state (use `gamepad1.toState()`)
     * @param gamepad2 Optional operator gamepad state (use `gamepad2.toState()`)
     */
    fun update(gamepad1: com.areslib.telemetry.GamepadState? = null, gamepad2: com.areslib.telemetry.GamepadState? = null) {
        if (!com.areslib.telemetry.RobotStatusTracker.isEnabled) {
            com.areslib.telemetry.RobotWebServer.stop()
        }
        com.areslib.telemetry.RobotStatusTracker.isEnabled = true
        com.areslib.telemetry.RobotStatusTracker.activeOpMode = "Active"
        // 0. Clear manual bulk caches at the beginning of the frame
        FtcPerformanceManager.clearBulkCaches()

        try {
            val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
            val dtSeconds = if (lastUpdateTime == 0L) 0.02 else (timestamp - lastUpdateTime) / 1000.0
            lastUpdateTime = timestamp

            // 0b. Update cached inputs for drivetrain motors from the bulk read registers
            mecanumIO.updateInputs()

            // 1. Read pinpoint sensors and update the EKF state store
            val poseUpdate = pinpointIO?.getPoseUpdate() ?: RobotAction.PoseUpdate(
                xMeters = 0.0,
                yMeters = 0.0,
                headingRadians = 0.0,
                timestampMs = timestamp
            )
            store.dispatch(poseUpdate)

            // 2. Update visual AprilTag observations
            updateVision(timestamp)

            // 3. Process kinematics using current State targets
            val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
            val vx = store.state.drive.xVelocityMetersPerSecond * maxSpeed
            val vy = store.state.drive.yVelocityMetersPerSecond * maxSpeed
            val omega = store.state.drive.angularVelocityRadiansPerSecond * maxSpeed
            
            // Field-centric vs Robot-centric coordinate transformation
            val chassisSpeeds = if (store.state.drive.isFieldCentric) {
                ChassisSpeeds.fromFieldRelativeSpeeds(
                    vx, vy, omega,
                    com.areslib.math.Rotation2d(store.state.drive.poseEstimator.estimatedPose.heading.radians)
                )
            } else {
                ChassisSpeeds(vx, vy, omega)
            }
            val wheelSpeeds = kinematics.toWheelSpeeds(chassisSpeeds)

            // 4. Fetch voltage sensor for sag compensation (rate-limited to 10Hz/100ms to eliminate blocking JNI overhead)
            if (timestamp - lastVoltageReadTime > 100 || lastVoltageReadTime == 0L) {
                lastVoltageReadTime = timestamp
                val voltageSensors = hardwareMap.getAll(com.qualcomm.robotcore.hardware.VoltageSensor::class.java)
                val newVoltage = if (voltageSensors.isNotEmpty()) {
                    voltageSensors[0].voltage
                } else {
                    12.0
                }
                
                // Apply a low-pass filter (time constant ~100ms) to prevent positive feedback sag oscillations during rapid acceleration
                val alpha = dtSeconds / (0.1 + dtSeconds)
                cachedBatteryVoltage = (cachedBatteryVoltage * (1.0 - alpha)) + (newVoltage * alpha)
            }
            val batteryVoltage = cachedBatteryVoltage

            // 4b. Brownout protection — graduated power scaling on voltage sag
            brownoutGuard.update(batteryVoltage)
            var effectiveScale = brownoutGuard.powerScale

            // 4c. Floodgate current protection — throttle on overload (or software fallback)
            floodgate?.let { fg ->
                fg.update()
                if (fg.isOverloadWarning()) {
                    // Graduated: scale inversely with fuse thermal load
                    val fuseScale = (1.0 - fg.fuseThermalLoadPercent / 100.0).coerceIn(0.2, 1.0)
                    effectiveScale = minOf(effectiveScale, fuseScale)
                }
            } ?: currentBudgetManager?.let { cbm ->
                cbm.update(batteryVoltage, enableCalibration = true)
                effectiveScale = minOf(effectiveScale, cbm.powerScale)
            }

            // Apply battery-compensated voltage vectors with power scaling exactly once
            mecanumIO.apply(
                speeds = wheelSpeeds.normalize(mecanumIO.maxWheelSpeedMetersPerSecond),
                batteryVolts = batteryVoltage,
                dtSeconds = dtSeconds,
                powerScale = effectiveScale
            )
            // Update local estimation tracking power scale
            mecanumIO.applyPowerScale(effectiveScale)

            // 5. Publish EVERYTHING to NT4 + CSV + local telemetry
            publishTelemetry(timestamp, dtSeconds, batteryVoltage, gamepad1, gamepad2)

            // 6. Populate and log the raw inputs frame for deterministic simulator replay
            val inputsFrame = com.areslib.logging.RobotInputsFramePool.rent()
            inputsFrame.timestampMs = timestamp

            // Populate odometry inputs
            inputsFrame.odometryInputs.posX = poseUpdate.xMeters
            inputsFrame.odometryInputs.posY = poseUpdate.yMeters
            inputsFrame.odometryInputs.heading = poseUpdate.headingRadians
            inputsFrame.odometryInputs.velX = store.state.drive.xVelocityMetersPerSecond
            inputsFrame.odometryInputs.velY = store.state.drive.yVelocityMetersPerSecond
            inputsFrame.odometryInputs.headingVelocity = store.state.drive.angularVelocityRadiansPerSecond
            inputsFrame.odometryInputs.timestampMs = timestamp

            // Populate IMU inputs
            inputsFrame.imuInputs.headingRadians = poseUpdate.headingRadians
            inputsFrame.imuInputs.pitchRadians = 0.0
            inputsFrame.imuInputs.rollRadians = 0.0
            inputsFrame.imuInputs.yawVelocityRadPerSec = store.state.drive.angularVelocityRadiansPerSecond
            inputsFrame.imuInputs.timestampMs = timestamp

            // Populate vision inputs
            inputsFrame.visionInputs.isConnected = limelightIO != null
            inputsFrame.visionInputs.measurements = visionInputs.measurements

            // Log the frame asynchronously
            inputLogger.logFrame(inputsFrame)
        } catch (e: Throwable) {
            System.err.println("FtcMecanumRobot: Exception in update loop: ${e.message}")
            e.printStackTrace()
            // Stop all motors safely
            try {
                mecanumIO.apply(
                    speeds = com.areslib.kinematics.MecanumWheelSpeeds(0.0, 0.0, 0.0, 0.0),
                    batteryVolts = cachedBatteryVoltage,
                    dtSeconds = 0.02,
                    powerScale = 0.0
                )
            } catch (ex: Throwable) {
                System.err.println("FtcMecanumRobot: Failed to apply safety stop: ${ex.message}")
            }
            try {
                dataLoggingTelemetry.putString("Robot/Error", "FATAL CRASH: ${e.message}")
            } catch (_: Throwable) {}
        }
    }

    private fun updateVision(timestamp: Long) {
        val io = limelightIO ?: run {
            com.areslib.telemetry.RobotStatusTracker.visionConnected = false
            com.areslib.telemetry.RobotStatusTracker.visionStatus = "OFFLINE"
            return
        }

        io.updateInputs(visionInputs)
        if (visionInputs.measurements.isEmpty()) {
            lastVisionStatus = "NO TARGET"
            com.areslib.telemetry.RobotStatusTracker.visionConnected = visionInputs.isConnected
            com.areslib.telemetry.RobotStatusTracker.visionStatus = lastVisionStatus
            return
        }

        val measurement = visionInputs.measurements[0]
        lastLimelightPose = measurement.targetPose.toPose2d()
        lastLimelightTimeMs = timestamp

        // Diagnose why EKF might reject the measurement
        val robotPose = store.state.drive.poseEstimator.estimatedPose
        val robotHeading = robotPose.heading.radians
        val tagPose3d = measurement.targetPose
        val tagPose2d = tagPose3d.toPose2d()
        val dx = tagPose2d.x - robotPose.x
        val dy = tagPose2d.y - robotPose.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val tagYaw = tagPose3d.rotation.z
        val headingDiff = com.areslib.math.InputMath.wrapAngle(tagYaw - robotHeading)

        lastVisionStatus = checkVisionOutlierRejection(measurement, distance, headingDiff)

        // 1. One-time absolute snap during initialization to bypass outlier lockout
        if (isInInit) {
            if (!hasInitializedPoseWithVision && measurement.ambiguity < 0.05) {
                val snapPose = measurement.targetPose.toPose2d()
                pinpointIO?.initialize(snapPose, resetHardware = false)
                hasInitializedPoseWithVision = true
                lastVisionStatus = "INIT_ALIGN_SNAP"
                store.dispatch(RobotAction.PoseUpdate(
                    xMeters = snapPose.x,
                    yMeters = snapPose.y,
                    headingRadians = snapPose.heading.radians,
                    timestampMs = timestamp,
                    isReset = true
                ))
            }
        } else {
            // Kidnapped Robot Recovery (Active Play)
            val isRejected = lastVisionStatus.startsWith("REJ_")
            val isHighConfidence = measurement.ambiguity < 0.05
            val isStationary = store.state.drive.xVelocityMetersPerSecond == 0.0 &&
                               store.state.drive.yVelocityMetersPerSecond == 0.0 &&
                               store.state.drive.angularVelocityRadiansPerSecond == 0.0

            if (isRejected && isHighConfidence && isStationary) {
                consecutiveVisionRejections++
                if (consecutiveVisionRejections >= 10) {
                    val snapPose = measurement.targetPose.toPose2d()
                    pinpointIO?.initialize(snapPose, resetHardware = false)
                    consecutiveVisionRejections = 0
                    lastVisionStatus = "RESEED_SNAP"
                    store.dispatch(RobotAction.PoseUpdate(
                        xMeters = snapPose.x,
                        yMeters = snapPose.y,
                        headingRadians = snapPose.heading.radians,
                        timestampMs = timestamp,
                        isReset = true
                    ))
                }
            } else {
                consecutiveVisionRejections = 0
            }
        }

        store.dispatch(RobotAction.VisionMeasurementsReceived(
            visionInputs.measurements,
            timestamp,
            null
        ))

        com.areslib.telemetry.RobotStatusTracker.visionConnected = visionInputs.isConnected
        com.areslib.telemetry.RobotStatusTracker.visionStatus = lastVisionStatus
    }

    private fun checkVisionOutlierRejection(
        measurement: com.areslib.state.VisionMeasurement,
        distance: Double,
        headingDiff: Double
    ): String {
        val tagPose3d = measurement.targetPose
        val tagPose2d = tagPose3d.toPose2d()
        val filterConfig = store.state.vision.filterConfig

        return when {
            measurement.ambiguity > filterConfig.maxAmbiguity -> {
                String.format("REJ_AMBIG (%.2f > %.2f)", measurement.ambiguity, filterConfig.maxAmbiguity)
            }
            tagPose3d.x < filterConfig.minFieldX || tagPose3d.x > filterConfig.maxFieldX ||
            tagPose3d.y < filterConfig.minFieldY || tagPose3d.y > filterConfig.maxFieldY ||
            tagPose3d.z < filterConfig.minFieldZ || tagPose3d.z > filterConfig.maxFieldZ -> {
                String.format("REJ_BOUNDS (Z: %.2f)", tagPose3d.z)
            }
            distance > filterConfig.maxDistanceMeters -> {
                String.format("REJ_DIST (%.2fm > %.2fm)", distance, filterConfig.maxDistanceMeters)
            }
            kotlin.math.abs(headingDiff) > filterConfig.maxRotationDeviationRad -> {
                String.format("REJ_YAW (%.1f° > %.1f°)", Math.toDegrees(headingDiff), Math.toDegrees(filterConfig.maxRotationDeviationRad))
            }
            else -> {
                // Run a dry run of the EKF Mahalanobis rejection to see if that fails
                val currentEstimator = store.state.drive.poseEstimator
                if (currentEstimator.history.isNotEmpty()) {
                    val closestIndex = currentEstimator.history.indices.reversed().firstOrNull {
                        currentEstimator.history[it].timestampMs <= measurement.timestampMs
                    } ?: -1
                    if (closestIndex != -1) {
                        val baseEntry = currentEstimator.history[closestIndex]
                        val stdDevs = com.areslib.math.Vector3(0.05, 0.05, 0.1)
                        val numTags = visionInputs.measurements.size
                        val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())
                        val distFactor = kotlin.math.sqrt(1.0 + distance * distance)
                        
                        val scaledStdDevsX = stdDevs.x * (multiTagFactor * distFactor)
                        val scaledStdDevsY = stdDevs.y * (multiTagFactor * distFactor)
                        val scaledStdDevsZ = stdDevs.z * (multiTagFactor * distFactor)
                        
                        val rXX = scaledStdDevsX * scaledStdDevsX
                        val rYY = scaledStdDevsY * scaledStdDevsY
                        val rZZ = scaledStdDevsZ * scaledStdDevsZ
                        
                        val sXX = baseEntry.covariance.m00 + rXX
                        val sYY = baseEntry.covariance.m11 + rYY
                        val sZZ = baseEntry.covariance.m22 + rZZ
                        
                        val yX = tagPose2d.x - baseEntry.pose.x
                        val yY = tagPose2d.y - baseEntry.pose.y
                        val yZ = com.areslib.math.InputMath.wrapAngle(tagPose2d.heading.radians - baseEntry.pose.heading.radians)
                        
                        val dMSquared = (yX * yX / sXX) + (yY * yY / sYY) + (yZ * yZ / sZZ)
                        if (dMSquared > 12.0) {
                            String.format("REJ_MAHALANOBIS (%.2f > 12.0)", dMSquared)
                        } else {
                            "ACCEPTED"
                        }
                    } else {
                        "ACCEPTED (NO_HIST)"
                    }
                } else {
                    "ACCEPTED"
                }
            }
        }
    }

    private fun publishTelemetry(
        timestamp: Long,
        dtSeconds: Double,
        batteryVoltage: Double,
        gamepad1: com.areslib.telemetry.GamepadState?,
        gamepad2: com.areslib.telemetry.GamepadState?
    ) {
        // 5. Publish EVERYTHING to NT4 + CSV automatically
        // Flat telemetry keys for cloud ingestion/diagnostic sync
        dataLoggingTelemetry.putNumber("loop_time_ms", dtSeconds * 1000.0)
        dataLoggingTelemetry.putNumber("battery_voltage", batteryVoltage)
        dataLoggingTelemetry.putNumber("motor_lf_current", mecanumIO.flIO.currentAmps)
        dataLoggingTelemetry.putNumber("motor_rf_current", mecanumIO.frIO.currentAmps)
        dataLoggingTelemetry.putNumber("motor_lr_current", mecanumIO.blIO.currentAmps)
        dataLoggingTelemetry.putNumber("motor_rr_current", mecanumIO.brIO.currentAmps)
        
        val estPose = store.state.drive.poseEstimator.estimatedPose
        dataLoggingTelemetry.putNumber("pinpoint_x", estPose.x)
        dataLoggingTelemetry.putNumber("pinpoint_y", estPose.y)
        dataLoggingTelemetry.putNumber("pinpoint_heading", estPose.heading.radians)
        
        val rawOdomX = store.state.drive.odometryX
        val rawOdomY = store.state.drive.odometryY
        dataLoggingTelemetry.putNumber("ekf_drift_x", estPose.x - rawOdomX)
        dataLoggingTelemetry.putNumber("ekf_drift_y", estPose.y - rawOdomY)

        publisher.publish(store.state, gamepad1, gamepad2)

        // Publish physical motor telemetry (power, encoder positions, velocities, currents)
        dataLoggingTelemetry.putNumber("Drive/MotorPower_FL", mecanumIO.flIO.power * mecanumIO.flIO.powerScale)
        dataLoggingTelemetry.putNumber("Drive/MotorPower_FR", mecanumIO.frIO.power * mecanumIO.frIO.powerScale)
        dataLoggingTelemetry.putNumber("Drive/MotorPower_BL", mecanumIO.blIO.power * mecanumIO.blIO.powerScale)
        dataLoggingTelemetry.putNumber("Drive/MotorPower_BR", mecanumIO.brIO.power * mecanumIO.brIO.powerScale)

        dataLoggingTelemetry.putNumber("Drive/MotorEncoder_FL", mecanumIO.flIO.position)
        dataLoggingTelemetry.putNumber("Drive/MotorEncoder_FR", mecanumIO.frIO.position)
        dataLoggingTelemetry.putNumber("Drive/MotorEncoder_BL", mecanumIO.blIO.position)
        dataLoggingTelemetry.putNumber("Drive/MotorEncoder_BR", mecanumIO.brIO.position)

        dataLoggingTelemetry.putNumber("Drive/MotorVelocity_FL", mecanumIO.flIO.velocity)
        dataLoggingTelemetry.putNumber("Drive/MotorVelocity_FR", mecanumIO.frIO.velocity)
        dataLoggingTelemetry.putNumber("Drive/MotorVelocity_BL", mecanumIO.blIO.velocity)
        dataLoggingTelemetry.putNumber("Drive/MotorVelocity_BR", mecanumIO.brIO.velocity)

        dataLoggingTelemetry.putNumber("Drive/MotorCurrent_FL", mecanumIO.flIO.currentAmps)
        dataLoggingTelemetry.putNumber("Drive/MotorCurrent_FR", mecanumIO.frIO.currentAmps)
        dataLoggingTelemetry.putNumber("Drive/MotorCurrent_BL", mecanumIO.blIO.currentAmps)
        dataLoggingTelemetry.putNumber("Drive/MotorCurrent_BR", mecanumIO.brIO.currentAmps)

        // Publish Limelight / Vision pipeline telemetry
        dataLoggingTelemetry.putString("Vision/Status", lastVisionStatus)
        lastLimelightPose?.let { pose ->
            dataLoggingTelemetry.putDoubleArray(
                "AdvantageScope/VisionPose",
                doubleArrayOf(pose.x, pose.y, pose.heading.radians)
            )
            dataLoggingTelemetry.putNumber("Vision/Pose_X", pose.x)
            dataLoggingTelemetry.putNumber("Vision/Pose_Y", pose.y)
            dataLoggingTelemetry.putNumber("Vision/Pose_Heading", pose.heading.radians)
        }
        if (visionInputs.measurements.isNotEmpty()) {
            val primaryMeasurement = visionInputs.measurements[0]
            dataLoggingTelemetry.putNumber("Vision/Primary_TagId", primaryMeasurement.tagId.toDouble())
            dataLoggingTelemetry.putNumber("Vision/Primary_Ambiguity", primaryMeasurement.ambiguity)
        } else {
            dataLoggingTelemetry.putNumber("Vision/Primary_TagId", -1.0)
            dataLoggingTelemetry.putNumber("Vision/Primary_Ambiguity", 1.0)
        }

        // Publish all registered custom hardware devices automatically
        com.areslib.hardware.HardwareRegistry.publishAll(dataLoggingTelemetry)

        // 6. Driver Station local telemetry (human-readable summary)
        localTelemetry?.let { t ->
            // A. Pinpoint vs EKF Pose
            t.addData("EKF Pose (X, Y, Deg)", String.format("(%.2f, %.2f) %.1f°",
                store.state.drive.poseEstimator.estimatedPose.x,
                store.state.drive.poseEstimator.estimatedPose.y,
                Math.toDegrees(store.state.drive.poseEstimator.estimatedPose.heading.radians)
            ))
            t.addData("Raw Pinpoint (X, Y, Deg)", String.format("(%.2f, %.2f) %.1f°",
                store.state.drive.odometryX,
                store.state.drive.odometryY,
                Math.toDegrees(store.state.drive.odometryHeading)
            ))

            // Limelight Pose
            val llStr = lastLimelightPose?.let { pose ->
                val ageSec = (timestamp - lastLimelightTimeMs) / 1000.0
                String.format("(%.2f, %.2f) %.1f° (%.1fs ago)",
                    pose.x,
                    pose.y,
                    Math.toDegrees(pose.heading.radians),
                    ageSec
                )
            } ?: "NO TARGET"
            t.addData("Limelight Pose (X, Y, Deg)", llStr)
            t.addData("Vision Status", lastVisionStatus)

            // B. Motor Power values
            t.addData("Motor Powers", String.format("FL:%.2f | FR:%.2f | RL:%.2f | RR:%.2f",
                mecanumIO.flIO.power * mecanumIO.flIO.powerScale,
                mecanumIO.frIO.power * mecanumIO.frIO.powerScale,
                mecanumIO.blIO.power * mecanumIO.blIO.powerScale,
                mecanumIO.brIO.power * mecanumIO.brIO.powerScale
            ))

            // C. Current Draw
            val currentStr = if (floodgate != null) {
                String.format("%.1f A (Physical)", floodgate.current)
            } else {
                val estTotal = mecanumIO.flIO.currentAmps + mecanumIO.frIO.currentAmps + mecanumIO.blIO.currentAmps + mecanumIO.brIO.currentAmps
                String.format("%.1f A (Estimated)", estTotal)
            }
            t.addData("Current Draw", currentStr)

            // D. Battery and Timing Metrics
            t.addData("System Health", String.format("Battery: %.2fV | Loop: %.1fms", 
                batteryVoltage, 
                dtSeconds * 1000.0
            ))

            // E. Hardware Status
            t.addData("Sensors", String.format("Pinpoint: %s | Limelight: %s",
                if (pinpointIO != null) "ONLINE" else "OFFLINE",
                if (limelightIO != null) "ONLINE" else "OFFLINE"
            ))

            t.update()
        }
    }

    /**
     * Gracefully cleans up background logging threads and closes network telemetry.
     */
    fun close() {
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotWebServer.stop()
        dataLoggingTelemetry.close()
        inputLogger.stop()
        actionLogger.stop()
        pinpointIO?.close()
    }
}

