package com.areslib.action

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.areslib.reducer.rootReducer
import com.areslib.state.RobotState
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline deterministic replay tool.
 * Reconstructs exact sequence of RobotState states by re-playing logged RobotActions through rootReducer.
 */
object ActionReplay {
    private val gson = Gson()

    private val actionRegistry = ConcurrentHashMap<String, Class<out RobotAction>>(mapOf(
        "DriveHardwareUpdate" to RobotAction.DriveHardwareUpdate::class.java,
        "VisionUpdate" to RobotAction.VisionUpdate::class.java,
        "VisionMeasurementsReceived" to RobotAction.VisionMeasurementsReceived::class.java,
        "PoseUpdate" to RobotAction.PoseUpdate::class.java,
        "JoystickDriveIntent" to RobotAction.JoystickDriveIntent::class.java,
        "PathEventTriggered" to RobotAction.PathEventTriggered::class.java,
        "SetIntakeActive" to RobotAction.SetIntakeActive::class.java,
        "SetFlywheelActive" to RobotAction.SetFlywheelActive::class.java,
        "SetTransferActive" to RobotAction.SetTransferActive::class.java,
        "UpdateFlywheelRPM" to RobotAction.UpdateFlywheelRPM::class.java,
        "SetFlywheelTargetRPM" to RobotAction.SetFlywheelTargetRPM::class.java,
        "SetInventoryCount" to RobotAction.SetInventoryCount::class.java,
        "ChainPaths" to RobotAction.ChainPaths::class.java,
        "SwitchPath" to RobotAction.SwitchPath::class.java,
        "UpdatePathProgress" to RobotAction.UpdatePathProgress::class.java
    ))

    /**
     * Registers a custom action type for deserialization during replay.
     */
    fun registerAction(type: String, clazz: Class<out RobotAction>) {
        actionRegistry[type] = clazz
    }

    /**
     * Replays a JSONL log file back through the pure rootReducer (or a custom reducer), returning a sequence of RobotState states.
     */
    @JvmOverloads
    fun replayLog(logFile: File, reducer: (RobotState, RobotAction) -> RobotState = ::rootReducer): List<RobotState> {
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
                        currentState = reducer(currentState, action)
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

            val actionClass = actionRegistry[type]

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
