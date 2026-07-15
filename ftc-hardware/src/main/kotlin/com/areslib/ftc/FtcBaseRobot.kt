package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.subsystem.AresRobot
import com.areslib.ftc.drivetrain.PinpointIO
import com.areslib.ftc.vision.FtcLimelightIO
import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.CompositeVisionIO
import com.areslib.ftc.vision.FtcVisionTracker
import com.areslib.ftc.telemetry.FtcTelemetryManager
import com.areslib.ftc.power.FtcPowerManager
import com.areslib.action.RobotAction
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.logging.populate
import com.areslib.hardware.sensor.ImuIO

/**
 * Abstract base class for all FTC robots.
 * Manages the unified hardware registries, performance parameters,
 * power managers, telemetry pipelines, and vision trackers.
 */
abstract class FtcBaseRobot @kotlin.jvm.JvmOverloads constructor(
    val hardwareMap: HardwareMap,
    val pinpointName: String? = "pinpoint",
    val limelightName: String? = "limelight",
    val imuName: String? = "imu",
    protected val localTelemetry: Telemetry? = null,
    
    // EKF Process noise
    val odomQx: Double = 0.01,
    val odomQy: Double = 0.01,
    val odomQtheta: Double = 0.01,
    
    // Pinpoint physical parameters
    val pinpointXOffsetMm: Double = 0.0,
    val pinpointYOffsetMm: Double = 0.0,
    val pinpointEncoderResolution: Double? = null,
    val pinpointXDirection: com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection = com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection.FORWARD,
    val pinpointYDirection: com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection = com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection.FORWARD,
    
    // Vision Configuration
    val visionStdDevs: com.areslib.math.geometry.Vector3 = com.areslib.math.geometry.Vector3(0.05, 0.05, 0.1),
    val visionFilterConfig: com.areslib.hardware.vision.VisionFilterConfig = com.areslib.hardware.vision.VisionFilterConfig.ftcDefaults()
) : AresRobot(
    initialState = com.areslib.state.RobotState(
        vision = com.areslib.state.VisionState(
            filterConfig = visionFilterConfig
        )
    )
) {

    init {
        com.areslib.ftc.hardware.FtcPerformanceManager.initialize(hardwareMap)
        com.areslib.telemetry.RobotWebServer.start()
        com.areslib.logging.LogManagerServer.startServer()
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotStatusTracker.activeOpMode = "Init"
        activeInstance = this

        com.areslib.math.estimation.PoseEstimator.qX = odomQx
        com.areslib.math.estimation.PoseEstimator.qY = odomQy
        com.areslib.math.estimation.PoseEstimator.qTheta = odomQtheta
    }

    companion object {
        val isAndroid: Boolean by lazy {
            val javaVendor = System.getProperty("java.vendor") ?: ""
            javaVendor.contains("Android", ignoreCase = true) || java.io.File("/sdcard").exists()
        }
        @Volatile
        @JvmStatic
        var activeInstance: FtcBaseRobot? = null
    }

    // Telemetry & recording pipelines
    val telemetryManager = FtcTelemetryManager(store)

    // Electrical and brownout manager
    val powerManager = FtcPowerManager(hardwareMap)

    // 1. Core Sensors
    val pinpointIO: PinpointIO? = try {
        pinpointName?.let { name ->
            val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, name)
            PinpointIO(
                driver = pinpointDriver,
                xOffsetMm = pinpointXOffsetMm,
                yOffsetMm = pinpointYOffsetMm,
                encoderResolution = pinpointEncoderResolution,
                xDirection = pinpointXDirection,
                yDirection = pinpointYDirection
            )
        }
    } catch (_: Throwable) {
        null
    }

    val imuIO: ImuIO? = try {
        imuName?.let { name ->
            val imuDriver = hardwareMap.get(com.qualcomm.robotcore.hardware.IMU::class.java, name)
            com.areslib.ftc.hardware.FtcImu(imuDriver)
        }
    } catch (_: Throwable) {
        null
    }

    val limelightIO: VisionIO? = try {
        limelightName?.let { namesStr ->
            val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            when {
                names.size > 1 -> {
                    val ios = names.map { name ->
                        val limelightDriver = hardwareMap.get(Limelight3A::class.java, name)
                        FtcLimelightIO(limelightDriver)
                    }
                    CompositeVisionIO(ios)
                }
                names.size == 1 -> {
                    val limelightDriver = hardwareMap.get(Limelight3A::class.java, names[0])
                    FtcLimelightIO(limelightDriver)
                }
                else -> null
            }
        }
    } catch (_: Throwable) {
        null
    }

    // Vision Tracker and Outlier Snapper
    val visionTracker = FtcVisionTracker(store, limelightIO, pinpointIO, visionStdDevs)

    private var lastPinpointWarningTime = 0L
    protected var lastUpdateTime = 0L

    private var hasReadSensorsThisFrame = false
    private var lastPoseUpdateTimestamp = 0L

    // Profiling: sub-timings from readSensors() stored for driver station display
    private var profBulkCacheMs = 0.0
    private var profHardwareInputsMs = 0.0
    private var profPinpointMs = 0.0
    private var profVisionMs = 0.0
    private var loopOverrunCount = 0

    /**
     * Synchronously reads pinpoint and Limelight visual tracking sensors,
     * updates the EKF state immediately, and clears REV expansion hub bulk caches.
     * This can be called at the start of an OpMode loop to eliminate one-frame loop latency.
     */
    fun readSensors() {
        if (hasReadSensorsThisFrame) return
        hasReadSensorsThisFrame = true

        // === PROFILING: sub-section timing inside readSensors ===
        val s0 = System.nanoTime()

        // 0. Clear manual bulk caches at the beginning of the frame
        com.areslib.ftc.hardware.FtcPerformanceManager.clearBulkCaches()
        val s1 = System.nanoTime()

        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        
        // 0b. Read inputs from robot-specific sensors (e.g. drive encoders)
        updateHardwareInputs()
        val s2 = System.nanoTime()

        // 1. Read pinpoint sensors and update EKF pose estimation
        val poseUpdate = pinpointIO?.getPoseUpdate() ?: getFallbackPoseUpdate(timestamp)
        val s3 = System.nanoTime()
        
        val isPinpointStale = pinpointIO != null && poseUpdate.timestampMs != 0L && (timestamp - poseUpdate.timestampMs) > 100
        val age = timestamp - poseUpdate.timestampMs
        when {
            isPinpointStale && (timestamp - lastPinpointWarningTime > 2000L) -> {
                System.err.println("FtcBaseRobot: Pinpoint pose update is stale! Age: ${age}ms")
                lastPinpointWarningTime = timestamp
            }
        }
        store.dispatch(poseUpdate)

        // 2. Process AprilTags visual updates
        visionTracker.update(timestamp)
        val s4 = System.nanoTime()

        // Store readSensors sub-timings for driver station display
        profBulkCacheMs = (s1 - s0) / 1_000_000.0
        profHardwareInputsMs = (s2 - s1) / 1_000_000.0
        profPinpointMs = (s3 - s2) / 1_000_000.0
        profVisionMs = (s4 - s3) / 1_000_000.0

        // Publish to NT4 telemetry
        val dl = telemetryManager.dataLoggingTelemetry
        dl.putNumber("Profiling/BulkCacheClear_ms", profBulkCacheMs)
        dl.putNumber("Profiling/HardwareInputs_ms", profHardwareInputsMs)
        dl.putNumber("Profiling/Pinpoint_ms", profPinpointMs)
        dl.putNumber("Profiling/Vision_ms", profVisionMs)
    }

    protected open fun getFallbackPoseUpdate(timestampMs: Long): RobotAction.PoseUpdate {
        val heading = imuIO?.let {
            val inputs = com.areslib.hardware.sensor.ImuInputs()
            it.updateInputs(inputs)
            inputs.headingRadians
        } ?: 0.0
        return RobotAction.PoseUpdate(
            xMeters = 0.0,
            yMeters = 0.0,
            headingRadians = heading,
            timestampMs = timestampMs
        )
    }

    /**
     * Executes a single frame cycle of the robot's control loop.
     * Clears hardware caches, updates sensors, filters power, executes kinematics,
     * and streams telemetry to NetworkTables and local storage.
     */
    fun update(gamepad1: com.areslib.telemetry.GamepadState? = null, gamepad2: com.areslib.telemetry.GamepadState? = null) {
        if (!isAndroid && lastUpdateTime != 0L) {
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            val elapsed = now - lastUpdateTime
            if (elapsed < 20) {
                try {
                    Thread.sleep(20 - elapsed)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        if (!com.areslib.telemetry.RobotStatusTracker.isEnabled && com.areslib.telemetry.RobotStatusTracker.activeOpMode != "Init") {
            // Start WebServer if not already running when transitioning to enabled
            com.areslib.telemetry.RobotWebServer.start()
        } else if (!com.areslib.telemetry.RobotStatusTracker.isEnabled) {
            com.areslib.telemetry.RobotWebServer.stop()
        }
        
        if (com.areslib.telemetry.RobotStatusTracker.activeOpMode != "Init") {
            com.areslib.telemetry.RobotStatusTracker.isEnabled = true
        }

        try {
            val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
            val dtSeconds = if (lastUpdateTime == 0L) 0.02 else (timestamp - lastUpdateTime) / 1000.0
            lastUpdateTime = timestamp

            // === PROFILING: per-section timing (nanoTime for wall-clock accuracy) ===
            val t0 = System.nanoTime()

            // Ensure sensors are read
            readSensors()
            hasReadSensorsThisFrame = false // Reset for the next frame
            val t1 = System.nanoTime()

            // 3. Update voltage sag filter and brownout power scaling
            val effectiveScale = powerManager.update(dtSeconds, timestamp)
            val batteryVoltage = powerManager.batteryVoltage
            val t2 = System.nanoTime()

            // 4. Compute kinematics and apply outputs to actuators
            updateSubsystems(dtSeconds, batteryVoltage, effectiveScale)
            val t3 = System.nanoTime()

            // 4b. Display profiling on driver station telemetry
            val sensorsMs = (t1 - t0) / 1_000_000.0
            val powerMs = (t2 - t1) / 1_000_000.0
            val subsystemsMs = (t3 - t2) / 1_000_000.0
            localTelemetry?.let { t ->
                t.addData("--- PROFILING", "(ms) ---")
                t.addData("Sensors", "%.1f [bulk:%.1f hw:%.1f pp:%.1f vis:%.1f]",
                    sensorsMs, profBulkCacheMs, profHardwareInputsMs, profPinpointMs, profVisionMs)
                t.addData("Power", "%.1f", powerMs)
                t.addData("Subsystems", "%.1f", subsystemsMs)
                t.addData("TOTAL (no telem)", "%.1f", sensorsMs + powerMs + subsystemsMs)
            }

            // 5. Publish logging and diagnostics
            telemetryManager.publishFull(
                state = store.state,
                gamepad1 = gamepad1,
                gamepad2 = gamepad2,
                dtSeconds = dtSeconds,
                batteryVoltage = batteryVoltage,
                visionTracker = visionTracker,
                timestamp = timestamp,
                localTelemetry = localTelemetry,
                onSubclassPublish = { publishRobotTelemetry(timestamp) }
            )
            val t4 = System.nanoTime()

            // Publish per-section timing (ms with 2 decimal precision)
            val dl = telemetryManager.dataLoggingTelemetry
            val totalTimeMs = (t4 - t0) / 1_000_000.0
            if (totalTimeMs > 25.0) { // Threshold for overrun is 25ms
                loopOverrunCount++
            }
            dl.putNumber("Diagnostics/LoopOverruns", loopOverrunCount.toDouble())
            dl.putNumber("Profiling/ReadSensors_ms", (t1 - t0) / 1_000_000.0)
            dl.putNumber("Profiling/PowerManager_ms", (t2 - t1) / 1_000_000.0)
            dl.putNumber("Profiling/Subsystems_ms", (t3 - t2) / 1_000_000.0)
            dl.putNumber("Profiling/Telemetry_ms", (t4 - t3) / 1_000_000.0)
            dl.putNumber("Profiling/Total_ms", totalTimeMs)

            // 6. Record frame inputs for deterministic replay (Disabled: ActionLogger handles Redux replay natively)
            /*
            val inputsFrame = com.areslib.logging.RobotInputsFramePool.rent().apply {
                populate(
                    telemetryManager.runId,
                    telemetryManager.robotId,
                    timestamp,
                    poseUpdate,
                    store.state.drive,
                    limelightIO != null,
                    visionTracker.visionInputs.measurements
                )
            }
            telemetryManager.inputLogger.logFrame(inputsFrame)
            */

            // 7. Throttle the loop to ~50Hz (20ms) when running in desktop simulation
            if (!isAndroid) {
                val elapsed = com.areslib.util.RobotClock.currentTimeMillis() - timestamp
                val sleepTime = 20L - elapsed
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime)
                }
            }

        } catch (e: Throwable) {
            if (e is InterruptedException || e.cause is InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            System.err.println("FtcBaseRobot: Exception in update loop: ${e.message}")
            e.printStackTrace()
            safeHardware()
            try {
                telemetryManager.dataLoggingTelemetry.putString("Robot/Error", "FATAL CRASH: ${e.message}")
            } catch (_: Throwable) {}
        }
    }

    /**
     * Callback for polling subclass-specific inputs (e.g. drive encoder positions).
     */
    protected abstract fun updateHardwareInputs()

    /**
     * Callback for calculating subclass-specific kinematics and applying voltage power to motors.
     */
    protected abstract fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double)

    /**
     * Callback for publishing subclass-specific telemetry keys.
     */
    protected abstract fun publishRobotTelemetry(timestamp: Long)

    /**
     * Safe fallback state: immediately sets all actuator powers to zero.
     */
    abstract fun safeHardware()

    /**
     * Gracefully stops logging threads, closes network connections,
     * and clears registries to prevent memory/thread leakage.
     */
    open fun close() {
        activeInstance = null
        safeHardware()
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotWebServer.stop()
        telemetryManager.close()
        pinpointIO?.close()
        try {
            (imuIO as? AutoCloseable)?.close()
        } catch (_: Exception) {}
        try {
            (limelightIO as? AutoCloseable)?.close()
        } catch (_: Exception) {}
        com.areslib.hardware.HardwareRegistry.closeAll()
        com.areslib.ftc.hardware.FtcMotor.unregisterAll()
    }
}

