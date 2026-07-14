package com.areslib.logging

import com.areslib.math.geometry.Vector3
import com.areslib.math.geometry.Pose2d
import com.areslib.telemetry.CloudExporter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object CloudReplayProvider {
    /**
     * Fetches raw inputs for a given run from ARESWEB and runs sensory replay to produce a ReplaySummary.
     * @param runId The unique run ID of the match telemetry
     * @param ghostVisionStdDevs Custom standard deviation scaling (x, y, heading) to apply to the Ghost EKF estimator
     * @return ReplaySummary containing the complete step-by-step trace of both poses
     */
    fun loadRun(runId: String, ghostVisionStdDevs: Vector3? = null): ReplaySummary {
        val serverUrl = CloudExporter.areswebServerUrl
        val url = URL("$serverUrl/replay/$runId/inputs")
        var conn: HttpURLConnection? = null
        val lines = mutableListOf<String>()
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.trim().isNotEmpty()) {
                            lines.add(line)
                        }
                    }
                }
            } else {
                System.err.println("CloudReplayProvider: Failed to load run inputs from cloud! HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            System.err.println("CloudReplayProvider: Error fetching run inputs: ${e.message}")
        } finally {
            conn?.disconnect()
        }

        return if (lines.isNotEmpty()) {
            SensoryReplayRunner.replaySensoryLines(lines, ghostVisionStdDevs)
        } else {
            ReplaySummary(emptyList(), Pose2d(), Pose2d())
        }
    }
}
