package com.areslib.sim.field

import org.dyn4j.dynamics.Body
import org.dyn4j.geometry.Geometry
import org.dyn4j.world.World
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigurableGamePieceInteractionModelTest {

    @Test
    fun testIntakeCollectsPieceWithinRangeAndRespectsMaxCapacity() {
        val world = World<Body>()
        val robot = Body()
        val model = ConfigurableGamePieceInteractionModel(
            maxCapacity = 1,
            intakeRangeMeters = 0.35,
            intakeRadiusMeters = 0.15,
        )

        val piece1 = Body().apply {
            addFixture(Geometry.createCircle(0.05))
            translate(0.35, 0.0) // Positioned directly in intake zone
        }
        val piece2 = Body().apply {
            addFixture(Geometry.createCircle(0.05))
            translate(0.36, 0.0) // Also in zone
        }
        world.addBody(piece1)
        world.addBody(piece2)
        val gamePieces = mutableListOf(piece1, piece2)

        // Tick 1: Intake applied -> captures piece 1, reaches max capacity (1)
        val count1 = model.update(
            world = world,
            robotBody = robot,
            gamePieces = gamePieces,
            intakeApplied = true,
            flywheelApplied = false,
            transferApplied = false,
            currentInventoryCount = 0,
            robotHeading = 0.0,
            robotX = 0.0,
            robotY = 0.0,
        )
        assertEquals(1, count1)
        assertEquals(1, gamePieces.size)

        // Tick 2: Intake applied with capacity already full -> rejects piece 2
        val count2 = model.update(
            world = world,
            robotBody = robot,
            gamePieces = gamePieces,
            intakeApplied = true,
            flywheelApplied = false,
            transferApplied = false,
            currentInventoryCount = 1,
            robotHeading = 0.0,
            robotX = 0.0,
            robotY = 0.0,
        )
        assertEquals(1, count2)
        assertEquals(1, gamePieces.size)
    }

    @Test
    fun testLaunchingPieceDecrementsInventoryAndSpawnsProjectile() {
        val world = World<Body>()
        val robot = Body()
        val model = ConfigurableGamePieceInteractionModel(maxCapacity = 2)
        val gamePieces = mutableListOf<Body>()

        val newCount = model.update(
            world = world,
            robotBody = robot,
            gamePieces = gamePieces,
            intakeApplied = false,
            flywheelApplied = true,
            transferApplied = true,
            currentInventoryCount = 1,
            robotHeading = 0.0,
            robotX = 0.0,
            robotY = 0.0,
        )
        assertEquals(0, newCount)
        assertEquals(1, gamePieces.size)
    }
}
