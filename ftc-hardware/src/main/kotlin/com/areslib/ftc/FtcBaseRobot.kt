package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.subsystem.AresRobot
import com.areslib.ftc.PinpointIO
import com.areslib.hardware.ftc.vision.FtcLimelightIO
import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.CompositeVisionIO
import com.areslib.ftc.vision.FtcVisionTracker
import com.areslib.ftc.telemetry.FtcTelemetryManager
import com.areslib.ftc.power.FtcPowerManager
import com.areslib.action.RobotAction
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.logging.populate

/**
 * Abstract base class for all FTC robots.
 * Manages the unified hardware registries, performance parameters,
 * power managers, telemetry pipelines, and vision trackers.
 */
abstract class FtcBaseRobot @kotlin.jvm.JvmOverloads constructor(
    val hardwareMap: HardwareMap,
    val pinpointName: String? = "pinpoint",
    val limelightName: String? = "limelight",
    protected val localTelemetry: Telemetry? = null
) : AresRobot() {

    init {
        com.areslib.ftc.hardware.FtcPerformanceManager.initialize(hardwareMap)
        com.areslib.telemetry.RobotWebServer.start()
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotStatusTracker.activeOpMode = "Init"
        activeInstance = this
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
            PinpointIO(pinpointDriver)
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
    val visionTracker = FtcVisionTracker(store, limelightIO, pinpointIO)

    private var lastPinpointWarningTime = 0L
    protected var lastUpdateTime = 0L

    /**
     * Executes a single frame cycle of the robot's control loop.
     * Clears hardware caches, updates sensors, filters power, executes kinematics,
     * and streams telemetry to NetworkTables and local storage.
     */
    fun update(gamepad1: com.areslib.telemetry.GamepadState? = null, gamepad2: com.areslib.telemetry.GamepadState? = null) {
        if (!com.areslib.telemetry.RobotStatusTracker.isEnabled) {
            com.areslib.telemetry.RobotWebServer.stop()
        }
        com.areslib.telemetry.RobotStatusTracker.isEnabled = true
        com.areslib.telemetry.RobotStatusTracker.activeOpMode = "Active"

        // 0. Clear manual bulk caches at the beginning of the frame
        com.areslib.ftc.hardware.FtcPerformanceManager.clearBulkCaches()

        try {
            val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
            val dtSeconds = if (lastUpdateTime == 0L) 0.02 else (timestamp - lastUpdateTime) / 1000.0
            lastUpdateTime = timestamp

            // 0b. Read inputs from robot-specific sensors (e.g. drive encoders)
            updateHardwareInputs()

            // 1. Read pinpoint sensors and update EKF pose estimation
            val poseUpdate = pinpointIO?.getPoseUpdate() ?: RobotAction.PoseUpdate(
                xMeters = 0.0,
                yMeters = 0.0,
                headingRadians = 0.0,
                timestampMs = timestamp
            )
            
            // Watchdog check for pinpoint sensor staleness - log warning instead of crashing
            if (pinpointIO != null && poseUpdate.timestampMs != 0L && (timestamp - poseUpdate.timestampMs) > 100) {
                val age = timestamp - poseUpdate.timestampMs
                if (timestamp - lastPinpointWarningTime > 2000L) {
                    System.err.println("FtcBaseRobot: Pinpoint pose update is stale! Age: ${age}ms")
                    lastPinpointWarningTime = timestamp
                }
            }
            store.dispatch(poseUpdate)

            // 2. Process AprilTags visual updates
            visionTracker.update(timestamp)

            // 3. Update voltage sag filter and brownout power scaling
            val effectiveScale = powerManager.update(dtSeconds, timestamp)
            val batteryVoltage = powerManager.batteryVoltage

            // 4. Compute kinematics and apply outputs to actuators
            updateSubsystems(dtSeconds, batteryVoltage, effectiveScale)

            // 5. Publish logging and diagnostics
            telemetryManager.publish(
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
        com.areslib.hardware.HardwareRegistry.closeAll()
        com.areslib.ftc.hardware.FtcMotor.unregisterAll()
    }
}
