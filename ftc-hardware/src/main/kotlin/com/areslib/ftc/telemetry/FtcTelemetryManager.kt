package com.areslib.ftc.telemetry

import com.areslib.subsystem.Store
import com.areslib.telemetry.NT4Telemetry
import com.areslib.telemetry.DataLoggingTelemetry
import com.areslib.telemetry.ARESNetworkStatePublisher
import com.areslib.logging.InputLogger
import com.areslib.action.ActionLogger
import com.areslib.state.RobotState
import com.areslib.telemetry.GamepadState
import com.areslib.hardware.HardwareRegistry

/**
 * Manages the robot's data telemetry, AdvantageScope NT4 networking,
 * and background file logging pipelines (input and action JSONL loggers).
 */
class FtcTelemetryManager(private val store: Store) : AutoCloseable {
    val nt4 = NT4Telemetry()
    val dataLoggingTelemetry = DataLoggingTelemetry(nt4)
    val publisher = ARESNetworkStatePublisher(dataLoggingTelemetry)

    val inputLogger = InputLogger()
    val actionLogger = ActionLogger()

    init {
        // Intercept and record all dispatched store actions asynchronously
        store.actionListener = { actionLogger.logAction(it) }
        HardwareRegistry.registerCloseable(this)
    }

    /**
     * Publishes the current state and driver gamepad states to NetworkTables.
     */
    fun publish(state: RobotState, gamepad1: GamepadState?, gamepad2: GamepadState?) {
        publisher.publish(state, gamepad1, gamepad2)
    }

    /**
     * Gracefully stops file logging threads and closes network streams.
     */
    override fun close() {
        dataLoggingTelemetry.close()
        inputLogger.stop()
        actionLogger.stop()
    }
}
