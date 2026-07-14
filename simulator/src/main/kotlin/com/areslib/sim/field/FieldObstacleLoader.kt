package com.areslib.sim.field

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
    fun loadObstacles(world: World<Body>, jsonString: String, @Suppress("UNUSED_PARAMETER") inMeters: Boolean = false): List<Body> {
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
     * Parses the obstacles.json format exported from the ARES-Analytics Field Editor.
     * Maps polymorphic Circle and Polygon models to RobotFieldObstacle.
     */
    fun loadObstaclesFromAnalyticsJson(jsonString: String): List<RobotFieldObstacle> {
        val list = mutableListOf<RobotFieldObstacle>()
        try {
            val array = gson.fromJson(jsonString, com.google.gson.JsonArray::class.java) ?: return emptyList()
            for (i in 0 until array.size()) {
                val obj = array.get(i).asJsonObject
                val id = obj.get("id")?.asString ?: ""
                val name = obj.get("name")?.asString ?: ""
                val type = obj.get("type")?.asString ?: ""

                when {
                    type.contains("Circle") || (obj.has("radius") && !obj.has("width")) -> {
                        val centerX = obj.get("centerX")?.asDouble ?: 0.0
                        val centerY = obj.get("centerY")?.asDouble ?: 0.0
                        val radius = obj.get("radius")?.asDouble ?: 0.25
                        list.add(
                            RobotFieldObstacle(
                                id = id,
                                name = name,
                                x = centerX,
                                y = centerY,
                                width = radius, // use width as radius for circle shape
                                height = radius,
                                shape = "circle"
                            )
                        )
                    }
                    type.contains("Rectangle") || (obj.has("width") && obj.has("height")) -> {
                        val centerX = obj.get("centerX")?.asDouble ?: 0.0
                        val centerY = obj.get("centerY")?.asDouble ?: 0.0
                        val width = obj.get("width")?.asDouble ?: 0.5
                        val height = obj.get("height")?.asDouble ?: 0.5
                        val rotation = obj.get("rotation")?.asDouble ?: 0.0
                        list.add(
                            RobotFieldObstacle(
                                id = id,
                                name = name,
                                x = centerX,
                                y = centerY,
                                width = width,
                                height = height,
                                rotation = rotation,
                                shape = "rectangle"
                            )
                        )
                    }
                    type.contains("Polygon") || obj.has("vertices") -> {
                        val verticesArray = obj.getAsJsonArray("vertices")
                        val pointsList = mutableListOf<RobotFieldPoint>()
                        var sumX = 0.0
                        var sumY = 0.0
                        for (j in 0 until verticesArray.size()) {
                            val ptObj = verticesArray.get(j).asJsonObject
                            val px = ptObj.get("x")?.asDouble ?: 0.0
                            val py = ptObj.get("y")?.asDouble ?: 0.0
                            pointsList.add(RobotFieldPoint(px, py))
                            sumX += px
                            sumY += py
                        }
                        val count = verticesArray.size().toDouble()
                        val cx = if (count > 0) sumX / count else 0.0
                        val cy = if (count > 0) sumY / count else 0.0
                        list.add(
                            RobotFieldObstacle(
                                id = id,
                                name = name,
                                x = cx,
                                y = cy,
                                shape = "polygon",
                                points = pointsList
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to parse obstacles.json from ARES-Analytics: ${e.message}")
        }
        return list
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

        if (obs.shape.lowercase() == "polygon" && obs.points.isNotEmpty()) {
            val rawVertices = obs.points.map { p ->
                Vector2(p.x - obs.x, p.y - obs.y)
            }.toTypedArray()
            
            var decomposed = false
            try {
                var cleanedVertices = if (rawVertices.size > 3 && rawVertices.first().distance(rawVertices.last()) < 0.02) {
                    rawVertices.dropLast(1).toTypedArray()
                } else {
                    rawVertices
                }

                var crossArea = 0.0
                for (i in cleanedVertices.indices) {
                    val p1 = cleanedVertices[i]
                    val p2 = cleanedVertices[(i + 1) % cleanedVertices.size]
                    crossArea += (p1.x * p2.y - p2.x * p1.y)
                }
                if (crossArea < 0) { // Clockwise
                    cleanedVertices = cleanedVertices.reversedArray()
                }

                val decomposer = org.dyn4j.geometry.decompose.Bayazit()
                val convexShapes = decomposer.decompose(*cleanedVertices)
                for (convex in convexShapes) {
                    val fixture = BodyFixture(convex)
                    fixture.friction = obs.friction
                    fixture.restitution = obs.restitution
                    if (!obs.isBlocking) fixture.isSensor = true
                    body.addFixture(fixture)
                }
                decomposed = true
            } catch (e: Exception) {
                System.err.println("Failed to decompose solid polygon ${obs.name}, falling back to edge links: ${e.message}")
            }

            if (!decomposed) {
                try {
                    val links = Geometry.createLinks(rawVertices, true)
                    for (link in links) {
                        val fixture = BodyFixture(link)
                        fixture.friction = obs.friction
                        fixture.restitution = obs.restitution
                        if (!obs.isBlocking) fixture.isSensor = true
                        body.addFixture(fixture)
                    }
                } catch (e: Exception) {
                    System.err.println("Failed to create polygon links for ${obs.name}: ${e.message}")
                }
            }
        } else {
            val shape = if (obs.shape.lowercase() == "circle") {
                Geometry.createCircle(obs.width)
            } else {
                Geometry.createRectangle(obs.width, obs.height)
            }

            val fixture = BodyFixture(shape)
            fixture.friction = obs.friction
            fixture.restitution = obs.restitution
            if (!obs.isBlocking) {
                fixture.isSensor = true
            }
            body.addFixture(fixture)
        }

        body.translate(obs.x, obs.y)

        if (obs.rotation != 0.0) {
            body.rotate(Math.toRadians(obs.rotation), obs.x, obs.y)
        }

        body.setMass(MassType.INFINITE)
        body.userData = obs.name

        return body
    }
}
