package com.areslib.sim

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Vector2
import org.dyn4j.world.World
import com.areslib.state.RobotFieldConfig
import com.areslib.state.RobotFieldObstacle
import com.areslib.state.RobotFieldPoint

object FieldObstacleLoader {
    private val gson = Gson()

    /**
     * Parses a JSON string containing field obstacles and spawns them into the dyn4j World.
     * Consumes the RobotFieldConfig schema in meters directly.
     */
    fun loadObstacles(world: World<Body>, jsonString: String, inMeters: Boolean = false): List<Body> {
        try {
            val root = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
            if (root == null) return emptyList()
            
            // It could be a full RobotFieldConfig or a direct wrapper
            val obstaclesList = mutableListOf<RobotFieldObstacle>()
            if (root.has("obstacles") && !root.get("obstacles").isJsonNull) {
                val array = root.getAsJsonArray("obstacles")
                for (i in 0 until array.size()) {
                    obstaclesList.add(gson.fromJson(array.get(i), RobotFieldObstacle::class.java))
                }
            } else {
                // Try parsing as the full config class
                val config = gson.fromJson(jsonString, RobotFieldConfig::class.java)
                if (config != null) {
                    obstaclesList.addAll(config.obstacles)
                }
            }
            return loadObstacles(world, obstaclesList)
        } catch (e: Exception) {
            System.err.println("Failed to parse field obstacles JSON: ${e.message}")
            e.printStackTrace()
        }
        return emptyList()
    }

    /**
     * Loads a list of RobotFieldObstacle directly into the dyn4j World.
     */
    fun loadObstacles(world: World<Body>, obstacles: List<RobotFieldObstacle>): List<Body> {
        val spawnedBodies = mutableListOf<Body>()
        for (obs in obstacles) {
            val body = createBodyFromObstacle(obs)
            world.addBody(body)
            spawnedBodies.add(body)
        }
        return spawnedBodies
    }

    private fun createBodyFromObstacle(obs: RobotFieldObstacle): Body {
        val body = Body()

        val shape = if (obs.shape.lowercase() == "polygon" && obs.points.isNotEmpty()) {
            val vertices = obs.points.map { p ->
                Vector2(p.x - obs.x, p.y - obs.y)
            }.toTypedArray()
            Geometry.createPolygon(*vertices)
        } else {
            Geometry.createRectangle(obs.width, obs.height)
        }

        val fixture = BodyFixture(shape)
        fixture.friction = obs.friction
        fixture.restitution = obs.restitution
        body.addFixture(fixture)

        body.translate(obs.x, obs.y)

        if (obs.rotation != 0.0) {
            body.rotate(Math.toRadians(obs.rotation), obs.x, obs.y)
        }

        body.setMass(MassType.INFINITE)
        body.userData = obs.name

        return body
    }
}
