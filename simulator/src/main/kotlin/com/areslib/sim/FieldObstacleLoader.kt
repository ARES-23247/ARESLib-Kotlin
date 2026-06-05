package com.areslib.sim

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.world.World

object FieldObstacleLoader {
    private val gson = Gson()
    private const val INCHES_TO_METERS = 0.0254

    /**
     * Data class to represent parsed 2D obstacle specifications.
     */
    data class ObstacleSpec(
        val name: String,
        val type: String, // "rectangle" or "circle"
        val x: Double,      // in inches, bottom-left relative
        val y: Double,      // in inches, bottom-left relative
        val width: Double,  // in inches
        val height: Double, // in inches
        val rotation: Double // in degrees
    )

    /**
     * Parses a JSON string containing field obstacles and spawns them into the dyn4j World.
     * Translates visualizer coordinates (inches, bottom-left origin) to simulator coordinates (meters, center origin).
     */
    fun loadObstacles(world: World<Body>, jsonString: String): List<Body> {
        val spawnedBodies = mutableListOf<Body>()
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            if (!root.has("obstacles") || root.get("obstacles").isJsonNull) {
                return emptyList()
            }

            val obstaclesArray = root.getAsJsonArray("obstacles")
            for (i in 0 until obstaclesArray.size()) {
                val obsJson = obstaclesArray.get(i).asJsonObject
                
                val name = if (obsJson.has("name")) obsJson.get("name").asString else "Obstacle_$i"
                val type = if (obsJson.has("type")) obsJson.get("type").asString else "rectangle"
                val x = if (obsJson.has("x")) obsJson.get("x").asDouble else 72.0
                val y = if (obsJson.has("y")) obsJson.get("y").asDouble else 72.0
                val width = if (obsJson.has("width")) obsJson.get("width").asDouble else 24.0
                val height = if (obsJson.has("height")) obsJson.get("height").asDouble else 24.0
                val rotation = if (obsJson.has("rotation")) obsJson.get("rotation").asDouble else 0.0

                val spec = ObstacleSpec(name, type, x, y, width, height, rotation)
                val body = createBodyFromSpec(spec)
                world.addBody(body)
                spawnedBodies.add(body)
            }
        } catch (e: Exception) {
            System.err.println("Failed to parse field obstacles JSON: ${e.message}")
            e.printStackTrace()
        }
        return spawnedBodies
    }

    private fun createBodyFromSpec(spec: ObstacleSpec): Body {
        val body = Body()
        
        // 1. Convert dimensions from inches to meters
        val wMeters = spec.width * INCHES_TO_METERS
        val hMeters = spec.height * INCHES_TO_METERS

        // 2. Create dyn4j Shape
        val shape = when (spec.type.lowercase()) {
            "circle" -> Geometry.createCircle(wMeters / 2.0)
            else -> Geometry.createRectangle(wMeters, hMeters)
        }

        val fixture = BodyFixture(shape)
        // Set solid physical parameters
        fixture.friction = 0.5
        fixture.restitution = 0.1
        body.addFixture(fixture)

        // 3. Translate from Visualizer Coordinates (0 to 144 inches, origin bottom-left)
        // to Simulator Coordinates (meters, origin center)
        val xMeters = (spec.x - 72.0) * INCHES_TO_METERS
        val yMeters = (spec.y - 72.0) * INCHES_TO_METERS

        body.translate(xMeters, yMeters)

        // 4. Set rotation (convert from degrees to radians)
        if (spec.rotation != 0.0) {
            body.rotate(Math.toRadians(spec.rotation), xMeters, yMeters)
        }

        // 5. Make it static so it doesn't move during collisions
        body.setMass(MassType.INFINITE)
        body.userData = spec.name

        return body
    }
}
