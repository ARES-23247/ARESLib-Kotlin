package com.areslib.pathing

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.areslib.sequencer.*
import java.io.File

object PathPlannerAutoParser {
    private val gson = Gson()

    /**
     * Parses a PathPlanner .auto JSON string and compiles it into a Task.
     */
    fun parseAuto(
        jsonString: String,
        follower: HolonomicPathFollower,
        timestampMs: Long
    ): Task {
        val root = gson.fromJson(jsonString, JsonObject::class.java)
        val commandObj = root.getAsJsonObject("command") ?: error("No root 'command' object in .auto file")
        return parseCommandNode(commandObj, follower, timestampMs)
    }

    private fun parseCommandNode(
        node: JsonObject,
        follower: HolonomicPathFollower,
        timestampMs: Long
    ): Task {
        val type = node.get("type")?.asString ?: error("Command node missing 'type'")
        val data = node.getAsJsonObject("data") ?: error("Command node '$type' missing 'data'")

        return when (type.lowercase()) {
            "sequential" -> {
                val cmdsArray = data.getAsJsonArray("commands") ?: JsonArray()
                val tasks = mutableListOf<Task>()
                for (i in 0 until cmdsArray.size()) {
                    tasks.add(parseCommandNode(cmdsArray.get(i).asJsonObject, follower, timestampMs))
                }
                SequentialTaskGroup(tasks)
            }
            "parallel" -> {
                val cmdsArray = data.getAsJsonArray("commands") ?: JsonArray()
                val tasks = mutableListOf<Task>()
                for (i in 0 until cmdsArray.size()) {
                    tasks.add(parseCommandNode(cmdsArray.get(i).asJsonObject, follower, timestampMs))
                }
                ParallelTaskGroup(tasks)
            }
            "race" -> {
                val cmdsArray = data.getAsJsonArray("commands") ?: JsonArray()
                val tasks = mutableListOf<Task>()
                for (i in 0 until cmdsArray.size()) {
                    tasks.add(parseCommandNode(cmdsArray.get(i).asJsonObject, follower, timestampMs))
                }
                ParallelRaceGroup(tasks)
            }
            "deadline" -> {
                val cmdsArray = data.getAsJsonArray("commands") ?: JsonArray()
                if (cmdsArray.size() == 0) {
                    SequentialTaskGroup(emptyList())
                } else {
                    val deadlineTask = parseCommandNode(cmdsArray.get(0).asJsonObject, follower, timestampMs)
                    val otherTasks = mutableListOf<Task>()
                    for (i in 1 until cmdsArray.size()) {
                        otherTasks.add(parseCommandNode(cmdsArray.get(i).asJsonObject, follower, timestampMs))
                    }
                    ParallelDeadlineGroup(deadlineTask, otherTasks)
                }
            }
            "path" -> {
                val pathName = data.get("pathName")?.asString ?: error("Path command missing 'pathName'")
                val path = DynamicPathLoader.loadPath(pathName)
                FollowPathTask(follower, path)
            }
            "wait" -> {
                val waitTimeSec = data.get("waitTime")?.asDouble ?: error("Wait command missing 'waitTime'")
                val waitTimeMs = (waitTimeSec * 1000.0).toLong()
                TimeWaitTask(waitTimeMs)
            }
            "named" -> {
                val name = data.get("name")?.asString ?: error("Named command missing 'name'")
                NamedCommands.getCommand(name, timestampMs) ?: object : Task {
                    override val name = "DummyNamed($name)"
                    override fun isCompleted(state: com.areslib.state.RobotState, elapsedMs: Long) = true
                }
            }
            else -> error("Unknown command type: '$type'")
        }
    }
}
