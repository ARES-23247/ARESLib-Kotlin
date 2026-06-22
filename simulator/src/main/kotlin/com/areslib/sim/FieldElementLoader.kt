package com.areslib.sim

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.world.World
import com.areslib.state.RobotFieldConfig
import com.areslib.state.RobotFieldElementType
import com.areslib.state.RobotFieldElementInstance

object FieldElementLoader {
    private val gson = Gson()

    fun loadElements(world: World<Body>, jsonString: String): List<Body> {
        try {
            val config = gson.fromJson(jsonString, RobotFieldConfig::class.java)
            if (config != null) {
                return loadElements(world, config.elementTypes, config.elements)
            }
        } catch (e: Exception) {
            System.err.println("Failed to parse field elements JSON: ${e.message}")
            e.printStackTrace()
        }
        return emptyList()
    }

    fun loadElements(
        world: World<Body>,
        elementTypes: List<com.areslib.state.RobotFieldElementType>,
        elements: List<com.areslib.state.RobotFieldElementInstance>
    ): List<Body> {
        val spawnedBodies = mutableListOf<Body>()
        val typesMap = elementTypes.associateBy { it.id }

        for (el in elements) {
            val typeSpec = typesMap[el.elementTypeId] ?: continue
            val body = createBodyFromSpec(typeSpec, el.x, el.y, el.rotation, el.id)
            world.addBody(body)
            spawnedBodies.add(body)
        }
        return spawnedBodies
    }

    private fun createBodyFromSpec(
        type: com.areslib.state.RobotFieldElementType,
        x: Double,
        y: Double,
        rotation: Double,
        id: String
    ): Body {
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

        body.userData = id

        return body
    }
}
