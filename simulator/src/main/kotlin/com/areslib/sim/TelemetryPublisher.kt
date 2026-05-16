package com.areslib.sim

import com.areslib.state.RobotState
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.networktables.StructPublisher
import com.areslib.frc.RobotStateStruct
import edu.wpi.first.wpilibj.DataLogManager

object TelemetryPublisher {
    private val ntInst = NetworkTableInstance.getDefault()
    private val statePublisher: StructPublisher<RobotState>

    init {
        // Start DataLogManager for offline .wpilog generation
        DataLogManager.start()

        // Configure NT4 for live streaming
        ntInst.startServer()
        
        // Register the custom struct so NT4 knows how to serialize it
        statePublisher = ntInst.getStructTopic("ARES/RobotState", RobotStateStruct()).publish()
    }

    /**
     * Publishes the current state to NT4 and DataLog.
     */
    fun publish(state: RobotState) {
        statePublisher.set(state)
        // You could also log paths here if PathPlanner targets are in the state
    }

    /**
     * Shutdown telemetry.
     */
    fun stop() {
        ntInst.stopServer()
        DataLogManager.stop()
    }
}
