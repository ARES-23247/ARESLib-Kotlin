package com.areslib.sim.replay

import com.areslib.logging.CloudReplayProvider
import com.areslib.math.geometry.Vector3
import com.areslib.telemetry.NT4Telemetry
import com.areslib.telemetry.ReplayPublisher
import com.areslib.sim.network.TelemetryPublisher

/**
 * Handles cloud log fetching and step-by-step NetworkTables streaming for replay.
 */
object SimReplayEngine {

    /**
     * replayCloudRun declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun replayCloudRun(replayCloudId: String, customVisionStdDevs: Vector3?) {
        println("[Simulator] Replaying cloud run $replayCloudId...")
        try {
            TelemetryPublisher.javaClass // Ensure NT4 is initialized
            val nt4Telemetry = NT4Telemetry()
            val publisher = ReplayPublisher(nt4Telemetry)
            val summary = CloudReplayProvider.loadRun(replayCloudId, customVisionStdDevs)
            println("[Simulator] Fetched cloud run summary: ${summary.steps.size} steps. Streaming to NetworkTables...")
            publisher.publishReplay(summary)
            println("[Simulator] Cloud replay completed.")
        } catch (e: Exception) {
            System.err.println("Failed to replay cloud run: ${e.message}")
        }
    }
}
