package com.areslib.sim

import edu.wpi.first.networktables.NetworkTableInstance
import com.google.gson.Gson

data class DynamicElementPose(
    val id: String,
    val x: Double,
    val y: Double,
    val rotation: Double // in degrees
)

object NT4FieldPublisher {
    private val ntInst = NetworkTableInstance.getDefault()
    private val obstaclesPub = ntInst.getStringTopic("ARES/Field/Obstacles").publish()
    private val elementsPub = ntInst.getStringTopic("ARES/Field/Elements").publish()
    private val scoresPub = ntInst.getStringTopic("ARES/Field/Scores").publish()
    private val configIdPub = ntInst.getStringTopic("ARES/Field/ConfigId").publish()
    private val apriltagsPub = ntInst.getStringTopic("ARES/Field/AprilTags").publish()
    private val gson = Gson()

    fun publishConfigId(configId: String) {
        configIdPub.set(configId)
    }

    fun publishAprilTags(tags: List<com.areslib.state.RobotFieldAprilTag>) {
        val json = gson.toJson(tags)
        apriltagsPub.set(json)
    }

    fun publishObstacles(obstacles: List<com.areslib.state.RobotFieldObstacle>) {
        val json = gson.toJson(obstacles)
        obstaclesPub.set(json)
    }

    fun publishElements(elements: List<DynamicElementPose>) {
        val json = gson.toJson(elements)
        elementsPub.set(json)
    }

    fun publishScores(blueScore: Int, redScore: Int) {
        val scoreMap = mapOf("blue" to blueScore, "red" to redScore)
        scoresPub.set(gson.toJson(scoreMap))
    }
}
