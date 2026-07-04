package com.areslib.sim

import org.dyn4j.dynamics.Body
import org.dyn4j.world.World

interface SimInteractionModel {
    /**
     * Called every simulation tick to handle custom interactions.
     * @param world The Dyn4j physics world.
     * @param robotBody The physics body of the robot.
     * @param gamePieces The list of current game pieces in the simulation.
     * @param driverStation The virtual driver station providing inputs.
     * @param currentInventoryCount The current number of items held by the robot.
     * @param robotHeading The robot's current heading in radians.
     * @param robotX The robot's current X position.
     * @param robotY The robot's current Y position.
     * @return The updated inventory count.
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

class NoOpInteractionModel : SimInteractionModel {
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
        // Does nothing to the physics engine, just maintains the inventory count.
        return currentInventoryCount
    }
}
