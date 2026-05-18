package com.areslib.action

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.areslib.reducer.rootReducer
import com.areslib.state.RobotState
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Offline deterministic replay tool.
 * Reconstructs exact sequence of RobotState states by re-playing logged RobotActions through rootReducer.
 */
object ActionReplay {
    private val gson = Gson()

    /**
     * Replays a JSONL log file back through the pure rootReducer, returning a sequence of RobotState states.
     */
    fun replayLog(logFile: File): List<RobotState> {
        val states = mutableListOf<RobotState>()
        var currentState = RobotState()
        
        // Add initial state
        states.add(currentState)

        if (!logFile.exists()) return states

        BufferedReader(FileReader(logFile)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.trim().isNotEmpty()) {
                    val action = deserializeAction(line)
                    if (action != null) {
                        currentState = rootReducer(currentState, action)
                        states.add(currentState)
                    }
                }
                line = reader.readLine()
            }
        }

        return states
    }

    private fun deserializeAction(jsonLine: String): RobotAction? {
        return try {
            val envelope = JsonParser.parseString(jsonLine).asJsonObject
            val type = envelope.get("type").asString
            val payload = envelope.getAsJsonObject("payload")

            // Map simple name to actual action class
            val actionClass = when (type) {
                "DriveHardwareUpdate" -> RobotAction.DriveHardwareUpdate::class.java
                "VisionUpdate" -> RobotAction.VisionUpdate::class.java
                "VisionMeasurementsReceived" -> RobotAction.VisionMeasurementsReceived::class.java
                "PoseUpdate" -> RobotAction.PoseUpdate::class.java
                "JoystickDriveIntent" -> RobotAction.JoystickDriveIntent::class.java
                "PathEventTriggered" -> RobotAction.PathEventTriggered::class.java
                "SetIntakeActive" -> RobotAction.SetIntakeActive::class.java
                "SetFlywheelActive" -> RobotAction.SetFlywheelActive::class.java
                "SetTransferActive" -> RobotAction.SetTransferActive::class.java
                "UpdateFlywheelRPM" -> RobotAction.UpdateFlywheelRPM::class.java
                "SetInventoryCount" -> RobotAction.SetInventoryCount::class.java
                "ObstacleCostmapUpdate" -> RobotAction.ObstacleCostmapUpdate::class.java
                "ChainPaths" -> RobotAction.ChainPaths::class.java
                "SwitchPath" -> RobotAction.SwitchPath::class.java
                "UpdatePathProgress" -> RobotAction.UpdatePathProgress::class.java
                else -> null
            }

            if (actionClass != null) {
                gson.fromJson(payload, actionClass)
            } else {
                System.err.println("ActionReplay: Unknown action type '$type'")
                null
            }
        } catch (e: Exception) {
            System.err.println("ActionReplay: Failed to deserialize action line: ${e.message}")
            null
        }
    }
}
