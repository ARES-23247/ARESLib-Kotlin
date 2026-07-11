package com.areslib.ftc.telemetry

import com.areslib.subsystem.Store
import com.areslib.telemetry.NT4Telemetry
import com.areslib.telemetry.DataLoggingTelemetry
import com.areslib.telemetry.ARESNetworkStatePublisher
import com.areslib.action.ActionLogger
import com.areslib.telemetry.CloudExporter
import com.areslib.state.RobotState
import com.areslib.telemetry.GamepadState
import com.areslib.telemetry.ITelemetry
import com.areslib.telemetry.RobotTelemetryManager
import com.areslib.hardware.HardwareRegistry

import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.ftc.vision.FtcVisionTracker
import com.areslib.telemetry.logPose2d
import com.areslib.telemetry.logPoseArray2d
import com.areslib.math.toFormattedString

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
    val brownoutGuard = com.areslib.control.BrownoutGuard.ftcDefaults()

    override val customPublishers = mutableListOf<(RobotState, ITelemetry) -> Unit>()

    val actionLogger = ActionLogger(runId, robotId, 0, "BLUE")

    init {
        // Intercept and record all dispatched store actions asynchronously
        store.actionListener = { actionLogger.logAction(it) }
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
        val estPose = state.drive.poseEstimator.estimatedPose
        brownoutGuard.update(batteryVoltage)

        // Subclass-specific telemetry (motor powers, currents, custom subsystems)
        onSubclassPublish()

        publisher.publish(state, gamepad1, gamepad2, dtSeconds, batteryVoltage, brownoutGuard)

        // Log the complete state, motor currents, and EKF vision updates
        // Obsolete: Handled by Unified ARESDataLogger

        // Vision telemetry status
        dataLoggingTelemetry.putString("Vision/Status", visionTracker.lastVisionStatus)

        // Global custom hardware telemetry
        HardwareRegistry.publishAll(dataLoggingTelemetry)

        // Invoke all registered custom publishers
        for (i in 0 until customPublishers.size) {
            customPublishers[i](state, dataLoggingTelemetry)
        }

        // Human-readable local driver station console printouts
        localTelemetry?.let { t ->
            t.addData("EKF Pose (X, Y, Deg)", estPose.toFormattedString())
            val pinpointPose = com.areslib.math.Pose2d(
                state.drive.odometryX,
                state.drive.odometryY,
                com.areslib.math.Rotation2d(state.drive.odometryHeading)
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

        // Finalize frame and flush to loggers/network
        dataLoggingTelemetry.update()
    }

    /**
     * Captures motor telemetry. This MUST be called at the end of the OpMode loop,
     * after all motor powers have been written to the hardware.
     */
    fun publishMotors(batteryVoltage: Double) {
        // Obsolete: Handled by Unified ARESDataLogger
    }

    /**
     * Gracefully stops file logging threads and closes network streams.
     */
    override fun close() {
        dataLoggingTelemetry.close()
        actionLogger.stop()
    }
}
