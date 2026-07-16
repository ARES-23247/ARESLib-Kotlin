package com.areslib.ftc.telemetry

import com.areslib.Store
import com.areslib.telemetry.NT4Telemetry
import com.areslib.logging.DataLoggingTelemetry
import com.areslib.telemetry.ARESNetworkStatePublisher
import com.areslib.action.ActionLogger
import com.areslib.logging.CloudExporter
import com.areslib.state.RobotState
import com.areslib.telemetry.GamepadState
import com.areslib.telemetry.ITelemetry
import com.areslib.telemetry.RobotTelemetryManager
import com.areslib.hardware.HardwareRegistry

import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.ftc.vision.FtcVisionTracker
import com.areslib.telemetry.logPose2d
import com.areslib.telemetry.logPoseArray2d
import com.areslib.math.geometry.toFormattedString

/**
 * Manages the robot's data telemetry, AdvantageScope NT4 networking,
 * and background file logging pipelines (input and action JSONL loggers).
 */
class FtcTelemetryManager(private val store: Store) : RobotTelemetryManager {
    val runId = java.util.UUID.randomUUID().toString()
    val robotId = "ares_robot"

    val nt4 = NT4Telemetry()
    override val dataLoggingTelemetry = DataLoggingTelemetry(nt4)
    val publisher = ARESNetworkStatePublisher(dataLoggingTelemetry)
    val brownoutGuard = com.areslib.control.safety.BrownoutGuard.ftcDefaults()

    override val customPublishers = mutableListOf<(RobotState, ITelemetry) -> Unit>()

    var actionLogger = ActionLogger(runId, robotId, 0, "BLUE", "Init")
        private set
        
    // Timestamp tracking for local Driver Station telemetry throttling
    private var lastLocalTelemetryUpdateMs = 0L

    @Volatile private var isRunning = true
    private val telemetryQueue = java.util.concurrent.ArrayBlockingQueue<List<Pair<String, String>>>(3)
    @Volatile private var currentLocalTelemetry: Telemetry? = null
    val customDriverStationText = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val driverStationThread = kotlin.concurrent.thread(start = true, name = "ARES-DriverStation-Thread") {
        while (isRunning) {
            try {
                val snapshot = telemetryQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                val t = currentLocalTelemetry
                if (snapshot != null && t != null) {
                    // Drain the queue to the latest snapshot to avoid falling behind
                    var latest = snapshot
                    while (telemetryQueue.isNotEmpty()) {
                        latest = telemetryQueue.poll() ?: latest
                    }
                    latest.forEach { (key, value) ->
                        t.addData(key, value)
                    }
                    t.update()
                }
            } catch (e: InterruptedException) {
                // Thread interrupted
            } catch (e: Exception) {
                // Ignore background telemetry formatting errors
            }
        }
    }

    private var telemetryFrameCounter = 0

    /**
     * Disable NT4 streaming entirely (e.g. during official matches to save CPU/bandwidth).
     * Disk logging will still occur normally.
     */
    var enableNetworkStreaming: Boolean = true

    init {
        // Intercept and record all dispatched store actions asynchronously
        store.actionListener = { action -> actionLogger.logAction(action) }
        HardwareRegistry.registerCloseable(this)
    }

    /**
     * Publishes the current robot state via the core interface contract.
     * Invokes [ARESNetworkStatePublisher], [HardwareRegistry] telemetry,
     * and all registered [customPublishers].
     */
    override fun publish(
        state: RobotState,
        gamepad1: GamepadState?,
        gamepad2: GamepadState?,
        dtSeconds: Double,
        batteryVoltage: Double
    ) {
        val detectedMode = com.areslib.telemetry.RobotStatusTracker.activeOpMode
        if (detectedMode != actionLogger.mode) {
            actionLogger.stop()
            actionLogger = ActionLogger(runId, robotId, 0, "BLUE", detectedMode)
        }

        brownoutGuard.update(batteryVoltage)

        publisher.publish(state, gamepad1, gamepad2, dtSeconds, batteryVoltage, brownoutGuard)

        // Global custom hardware telemetry
        HardwareRegistry.publishAll(dataLoggingTelemetry)

        // Invoke all registered custom publishers
        for (i in 0 until customPublishers.size) {
            customPublishers[i](state, dataLoggingTelemetry)
        }

        // Finalize frame and flush to loggers/network
        dataLoggingTelemetry.update()
    }

    /**
     * Full FTC-specific publish method with vision tracker telemetry and local
     * driver station console output. Retained for backward compatibility with
     * existing OpMode code.
     */
    fun publishFull(
        state: RobotState,
        gamepad1: GamepadState?,
        gamepad2: GamepadState?,
        dtSeconds: Double,
        batteryVoltage: Double,
        visionTracker: FtcVisionTracker,
        timestamp: Long,
        localTelemetry: Telemetry?,
        onSubclassPublish: () -> Unit = {}
    ) {
        val detectedMode = com.areslib.telemetry.RobotStatusTracker.activeOpMode
        if (detectedMode != actionLogger.mode) {
            actionLogger.stop()
            actionLogger = ActionLogger(runId, robotId, 0, "BLUE", detectedMode)
        }

        // Throttle NT4 network writes to every 3rd frame (~17Hz) if enabled.
        // Disk logging still runs every frame via currentFrame accumulation.
        telemetryFrameCounter++
        val isNtFrame = enableNetworkStreaming && (telemetryFrameCounter % 3 == 0)
        dataLoggingTelemetry.ntEnabled = isNtFrame

        val estPose = state.drive.poseEstimator.estimatedPose
        brownoutGuard.update(batteryVoltage)

        // Subclass-specific telemetry (motor powers, currents, custom subsystems)
        onSubclassPublish()

        publisher.publish(state, gamepad1, gamepad2, dtSeconds, batteryVoltage, brownoutGuard)

        // Vision telemetry status
        dataLoggingTelemetry.putString("Vision/Status", visionTracker.lastVisionStatus)

        // Global custom hardware telemetry (also governed by ntEnabled flag)
        HardwareRegistry.publishAll(dataLoggingTelemetry)

        // Invoke all registered custom publishers
        for (i in 0 until customPublishers.size) {
            customPublishers[i](state, dataLoggingTelemetry)
        }

        currentLocalTelemetry = localTelemetry

        // Human-readable local driver station console printouts
        // Non-blocking architecture: string updates are pushed to a background thread queue. 
        // This completely eliminates the 15-30ms synchronous WiFi socket stalls from `Telemetry.update()`.
        localTelemetry?.let { t ->
            if (timestamp - lastLocalTelemetryUpdateMs >= 250L) { // 4Hz real-time updates!
                val snapshot = mutableListOf(
                    "EKF Pose (X, Y, Deg)" to estPose.toFormattedString(),
                    "Raw Pinpoint (X, Y, Deg)" to com.areslib.math.geometry.Pose2d(
                        state.drive.odometryX,
                        state.drive.odometryY,
                        com.areslib.math.geometry.Rotation2d(state.drive.odometryHeading)
                    ).toFormattedString(),
                    "Limelight Pose (X, Y, Deg)" to (visionTracker.lastLimelightPose?.let { pose ->
                        val ageSec = (timestamp - visionTracker.lastLimelightTimeMs) / 1000.0
                        "${pose.toFormattedString()} (${String.format("%.1f", ageSec)}s ago)"
                    } ?: "NO TARGET"),
                    "Vision Status" to visionTracker.lastVisionStatus
                )
                customDriverStationText.forEach { (k, v) -> snapshot.add(k to v) }
                telemetryQueue.offer(snapshot)
                lastLocalTelemetryUpdateMs = timestamp
            }
        }

        // Finalize frame: disk log always, NT4 flush only on NT frames
        dataLoggingTelemetry.update()

        // Reset NT4 enabled for any out-of-band puts between frames
        dataLoggingTelemetry.ntEnabled = true
    }

    /**
     * Captures motor telemetry. This MUST be called at the end of the OpMode loop,
     * after all motor powers have been written to the hardware.
     */
    @Suppress("UNUSED_PARAMETER")
    fun publishMotors(batteryVoltage: Double) {
        // Obsolete: Handled by Unified ARESDataLogger
    }

    /**
     * Gracefully stops file logging threads and closes network streams.
     */
    override fun close() {
        isRunning = false
        driverStationThread.interrupt()
        dataLoggingTelemetry.close()
        actionLogger.stop()
    }
}

