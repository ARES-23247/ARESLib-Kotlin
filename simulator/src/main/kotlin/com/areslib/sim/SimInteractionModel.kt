package com.areslib.sim

import org.dyn4j.dynamics.Body
import org.dyn4j.world.World
import com.areslib.sim.infra.VirtualDriverStation

/**
 * Interface defining custom robot-environment physics interaction logic in the Dyn4j simulator.
 *
 * Implementations process field elements, intake/outtake mechanics, scoring zones, and inventory tracking per physics tick.
 */
interface SimInteractionModel {
    /**
     * Called every simulation tick to handle custom physical interactions.
     *
     * @param world Active Dyn4j physics world instance.
     * @param robotBody Physics body representing the robot chassis.
     * @param gamePieces Mutable list of active game piece physics bodies on the field.
     * @param driverStation Virtual driver station input provider.
     * @param currentInventoryCount Current count of items/elements held by the robot.
     * @param robotHeading Robot heading orientation in radians (CCW positive).
     * @param robotX Robot field position X coordinate in meters.
     * @param robotY Robot field position Y coordinate in meters.
     * @return Updated item inventory count following interaction processing.
     */
    fun update(
        world: World<Body>,
        robotBody: Body,
        gamePieces: MutableList<Body>,
        driverStation: VirtualDriverStation,
        currentInventoryCount: Int,
        robotHeading: Double,
        robotX: Double,
        robotY: Double
    ): Int
}

/**
 * Default pass-through implementation of [SimInteractionModel] performing no physical manipulation of field elements.
 */
class NoOpInteractionModel : SimInteractionModel {
    /**
     * Pass-through update loop preserving the existing inventory count without modifying field bodies.
     */
    override fun update(
        world: World<Body>,
        robotBody: Body,
        gamePieces: MutableList<Body>,
        driverStation: VirtualDriverStation,
        currentInventoryCount: Int,
        robotHeading: Double,
        robotX: Double,
        robotY: Double
    ): Int {
        return currentInventoryCount
    }
}
