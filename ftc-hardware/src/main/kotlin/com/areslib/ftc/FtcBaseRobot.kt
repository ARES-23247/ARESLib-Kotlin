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
import com.areslib.telemetry.logPose2d
import com.areslib.telemetry.logPoseArray2d
import com.areslib.math.toFormattedString

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
    } catch (_: Throwable) {
        null
    }

    // Vision Tracker and Outlier Snapper
    val visionTracker = FtcVisionTracker(store, limelightIO, pinpointIO)

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
            
            // Watchdog check for pinpoint sensor staleness
            if (pinpointIO != null && poseUpdate.timestampMs != 0L && (timestamp - poseUpdate.timestampMs) > 100) {
                safeHardware()
                throw IllegalStateException("Pinpoint pose update is stale! Age: ${timestamp - poseUpdate.timestampMs}ms (exceeds 100ms threshold)")
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
            publishTelemetry(timestamp, dtSeconds, batteryVoltage, gamepad1, gamepad2)

            // 6. Record frame inputs for deterministic replay
            val inputsFrame = com.areslib.logging.RobotInputsFramePool.rent().apply {
                populate(
                    timestamp,
                    poseUpdate,
                    store.state.drive,
                    limelightIO != null,
                    visionTracker.visionInputs.measurements
                )
            }
            telemetryManager.inputLogger.logFrame(inputsFrame)

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

    private fun publishTelemetry(
        timestamp: Long,
        dtSeconds: Double,
        batteryVoltage: Double,
        gamepad1: com.areslib.telemetry.GamepadState?,
        gamepad2: com.areslib.telemetry.GamepadState?
    ) {
        telemetryManager.dataLoggingTelemetry.putNumber("loop_time_ms", dtSeconds * 1000.0)
        telemetryManager.dataLoggingTelemetry.putNumber("battery_voltage", batteryVoltage)

        val estPose = store.state.drive.poseEstimator.estimatedPose
        telemetryManager.dataLoggingTelemetry.logPose2d("pinpoint", estPose, useUnderscores = true, lowercase = true)
        
        val rawOdomX = store.state.drive.odometryX
        val rawOdomY = store.state.drive.odometryY
        telemetryManager.dataLoggingTelemetry.putNumber("ekf_drift_x", estPose.x - rawOdomX)
        telemetryManager.dataLoggingTelemetry.putNumber("ekf_drift_y", estPose.y - rawOdomY)

        // Subclass-specific telemetry (motor powers, currents, custom subsystems)
        publishRobotTelemetry(timestamp)

        telemetryManager.publish(store.state, gamepad1, gamepad2)

        // Vision telemetry
        telemetryManager.dataLoggingTelemetry.putString("Vision/Status", visionTracker.lastVisionStatus)
        visionTracker.lastLimelightPose?.let { pose ->
            telemetryManager.dataLoggingTelemetry.logPoseArray2d("AdvantageScope/VisionPose", pose)
            telemetryManager.dataLoggingTelemetry.logPose2d("Vision/Pose", pose, useUnderscores = true)
        }
        if (visionTracker.visionInputs.measurements.isNotEmpty()) {
            val primaryMeasurement = visionTracker.visionInputs.measurements[0]
            telemetryManager.dataLoggingTelemetry.putNumber("Vision/Primary_TagId", primaryMeasurement.tagId.toDouble())
            telemetryManager.dataLoggingTelemetry.putNumber("Vision/Primary_Ambiguity", primaryMeasurement.ambiguity)
        } else {
            telemetryManager.dataLoggingTelemetry.putNumber("Vision/Primary_TagId", -1.0)
            telemetryManager.dataLoggingTelemetry.putNumber("Vision/Primary_Ambiguity", 1.0)
        }

        // Global custom hardware telemetry
        com.areslib.hardware.HardwareRegistry.publishAll(telemetryManager.dataLoggingTelemetry)

        // Human-readable local driver station console printouts
        localTelemetry?.let { t ->
            t.addData("EKF Pose (X, Y, Deg)", estPose.toFormattedString())
            val pinpointPose = com.areslib.math.Pose2d(
                store.state.drive.odometryX,
                store.state.drive.odometryY,
                com.areslib.math.Rotation2d(store.state.drive.odometryHeading)
            )
            t.addData("Raw Pinpoint (X, Y, Deg)", pinpointPose.toFormattedString())

            val llStr = visionTracker.lastLimelightPose?.let { pose ->
                val ageSec = (timestamp - visionTracker.lastLimelightTimeMs) / 1000.0
                "${pose.toFormattedString()} (${String.format("%.1f", ageSec)}s ago)"
            } ?: "NO TARGET"
            t.addData("Limelight Pose (X, Y, Deg)", llStr)
            t.addData("Vision Status", visionTracker.lastVisionStatus)
            t.update()
        }
    }

    /**
     * Gracefully stops logging threads, closes network connections,
     * and clears registries to prevent memory/thread leakage.
     */
    open fun close() {
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotWebServer.stop()
        telemetryManager.close()
        pinpointIO?.close()
        com.areslib.hardware.HardwareRegistry.closeAll()
        com.areslib.ftc.hardware.FtcMotor.unregisterAll()
    }
}
