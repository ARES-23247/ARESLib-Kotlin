package com.areslib.sim

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.world.World

object FieldElementLoader {
    private val gson = Gson()

    data class ElementTypeSpec(
        val id: String,
        val name: String,
        val shape: String, // "box", "cylinder", "sphere"
        val width: Double,
        val height: Double,
        val depth: Double,
        val diameter: Double?,
        val color: String,
        val massKg: Double,
        val movable: Boolean
    )

    data class ElementInstanceSpec(
        val id: String,
        val elementTypeId: String,
        val x: Double,
        val y: Double,
        val rotation: Double
    )

    fun loadElements(world: World<Body>, jsonString: String): List<Body> {
        val spawnedBodies = mutableListOf<Body>()
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            if (!root.has("elementTypes") || root.get("elementTypes").isJsonNull ||
                !root.has("elements") || root.get("elements").isJsonNull) {
                return emptyList()
            }

            val typesArray = root.getAsJsonArray("elementTypes")
            val typesMap = mutableMapOf<String, ElementTypeSpec>()
            for (i in 0 until typesArray.size()) {
                val tJson = typesArray.get(i).asJsonObject
                val id = tJson.get("id").asString
                val name = tJson.get("name").asString
                val shape = tJson.get("shape").asString
                val width = tJson.get("width").asDouble
                val height = tJson.get("height").asDouble
                val depth = tJson.get("depth").asDouble
                val diameter = if (tJson.has("diameter") && !tJson.get("diameter").isJsonNull) tJson.get("diameter").asDouble else null
                val color = tJson.get("color").asString
                val massKg = tJson.get("massKg").asDouble
                val movable = tJson.get("movable").asBoolean

                typesMap[id] = ElementTypeSpec(id, name, shape, width, height, depth, diameter, color, massKg, movable)
            }

            val elementsArray = root.getAsJsonArray("elements")
            for (i in 0 until elementsArray.size()) {
                val elJson = elementsArray.get(i).asJsonObject
                val id = elJson.get("id").asString
                val typeId = elJson.get("elementTypeId").asString
                val x = elJson.get("x").asDouble
                val y = elJson.get("y").asDouble
                val rotation = elJson.get("rotation").asDouble

                val typeSpec = typesMap[typeId] ?: continue
                val body = createBodyFromSpec(typeSpec, x, y, rotation)
                world.addBody(body)
                spawnedBodies.add(body)
            }
        } catch (e: Exception) {
            System.err.println("Failed to parse field elements JSON: ${e.message}")
            e.printStackTrace()
        }
        return spawnedBodies
    }

    private fun createBodyFromSpec(type: ElementTypeSpec, x: Double, y: Double, rotation: Double): Body {
        val body = Body()

        val shape = when (type.shape.lowercase()) {
            "box" -> Geometry.createRectangle(type.width, type.height)
            else -> {
                val radius = (type.diameter ?: 0.15) / 2.0
                Geometry.createCircle(radius)
            }
        }

        val fixture = BodyFixture(shape)
        fixture.friction = 0.6
        fixture.restitution = 0.3

        // For a 2D physics engine, Mass = Density * Area.
        // We set fixture density so that calculated mass equals massKg.
        val area = shape.getArea()
        val density = if (area > 0) type.massKg / area else 1.0
        fixture.density = density

        body.addFixture(fixture)

        if (type.movable) {
            body.setMass(MassType.NORMAL)
            // Add carpet friction damping
            body.linearDamping = 2.0
            body.angularDamping = 2.0
        } else {
            body.setMass(MassType.INFINITE)
        }

        // Translate to simulator coordinates (meters relative to center origin)
        body.translate(x, y)

        if (rotation != 0.0) {
            body.rotate(Math.toRadians(rotation), x, y)
        }

        body.userData = type.name

        return body
    }
}
