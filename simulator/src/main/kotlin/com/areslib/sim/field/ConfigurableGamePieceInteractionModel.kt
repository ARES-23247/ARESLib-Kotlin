package com.areslib.sim.field

import com.areslib.sim.SimInteractionModel
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Vector2
import org.dyn4j.world.World
import kotlin.math.cos
import kotlin.math.sin

/**
 * Configurable physical game-piece intake, inventory capacity, and shooting/ejection interaction model.
 */
class ConfigurableGamePieceInteractionModel(
    /** Maximum number of game pieces the robot can physically hold simultaneously. */
    val maxCapacity: Int = 1,
    /** Forward distance from robot center to the intake collection point in meters. */
    val intakeRangeMeters: Double = 0.35,
    /** Radius of the intake capture zone in meters. */
    val intakeRadiusMeters: Double = 0.15,
    /** Linear launch impulse applied to ejected game pieces. */
    val launchImpulse: Double = 8.0,
    /** Diameter of simulated game pieces in meters. */
    val pieceDiameterMeters: Double = 0.15,
    /** Mass of simulated game piece in kilograms. */
    val pieceMassKg: Double = 0.24,
) : SimInteractionModel {

    private var transferWasApplied = false

    override fun update(
        world: World<Body>,
        robotBody: Body,
        gamePieces: MutableList<Body>,
        intakeApplied: Boolean,
        flywheelApplied: Boolean,
        transferApplied: Boolean,
        currentInventoryCount: Int,
        robotHeading: Double,
        robotX: Double,
        robotY: Double,
    ): Int {
        var newInventory = currentInventoryCount

        // 1. INTAKE LOGIC
        val frontX = robotX + cos(robotHeading) * intakeRangeMeters
        val frontY = robotY + sin(robotHeading) * intakeRangeMeters
        val captureRadiusSq = intakeRadiusMeters * intakeRadiusMeters

        if (intakeApplied && newInventory < maxCapacity) {
            for (index in gamePieces.indices) {
                val piece = gamePieces[index]
                val dx = piece.transform.translationX - frontX
                val dy = piece.transform.translationY - frontY
                if (dx * dx + dy * dy < captureRadiusSq) {
                    world.removeBody(piece)
                    gamePieces.removeAt(index)
                    newInventory++
                    break // Capture one element per step
                }
            }
        }

        // 2. LAUNCH / SCORING LOGIC
        if (transferApplied && !transferWasApplied && flywheelApplied && newInventory > 0) {
            val newPiece = Body()
            val radius = pieceDiameterMeters / 2.0
            val shape = Geometry.createCircle(radius)
            val fixture = BodyFixture(shape)
            fixture.friction = 0.6
            fixture.restitution = 0.3
            fixture.density = pieceMassKg / shape.getArea()
            newPiece.addFixture(fixture)
            newPiece.setMass(MassType.NORMAL)
            newPiece.linearDamping = 1.5
            newPiece.angularDamping = 1.5

            // Position projectile just forward of the chassis
            val spawnX = robotX + cos(robotHeading) * (intakeRangeMeters + radius + 0.05)
            val spawnY = robotY + sin(robotHeading) * (intakeRangeMeters + radius + 0.05)
            newPiece.translate(spawnX, spawnY)

            // Apply forward propulsion vector
            val forceX = cos(robotHeading) * launchImpulse
            val forceY = sin(robotHeading) * launchImpulse
            newPiece.applyImpulse(Vector2(forceX, forceY))

            world.addBody(newPiece)
            gamePieces.add(newPiece)
            newInventory--
        }

        transferWasApplied = transferApplied
        return newInventory
    }

    override fun reset() {
        transferWasApplied = false
    }
}
