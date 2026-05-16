package com.areslib.sim

import com.areslib.state.RobotState
import org.dyn4j.dynamics.Body
import org.dyn4j.world.World
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType

object DesktopSimLauncher {
    private const val TIMESTEP_MS = 20L
    private const val TIMESTEP_SEC = 0.02
    
    // FTC standard field size
    private const val FIELD_WIDTH = 3.65
    private const val FIELD_HEIGHT = 3.65

    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting ARESLib Desktop Simulation...")

        // 1. Initialize WPILib Telemetry
        println("Initializing Telemetry (NT4 & DataLog)...")
        // Trigger init block
        TelemetryPublisher.javaClass

        // 2. Setup Dyn4j World
        val world = World<Body>()
        world.setGravity(org.dyn4j.geometry.Vector2(0.0, 0.0)) // Top-down 2D view, no gravity

        // Create the Robot Body
        val robotBody = Body()
        // Standard FTC robot footprint (18x18 inches ~ 0.45x0.45 meters)
        robotBody.addFixture(Geometry.createRectangle(0.45, 0.45))
        robotBody.setMass(MassType.NORMAL)
        // Spawn robot in the exact center of the field (0, 0)
        robotBody.translate(0.0, 0.0)
        world.addBody(robotBody)

        // Add walls (static bodies)
        createWalls(world)

        println("Simulation Running at 50Hz. Press Ctrl+C to stop.")

        // 3. Simulation Loop
        var state = RobotState()
        
        while (true) {
            val startTime = System.currentTimeMillis()

            // Step physics engine
            world.step(1, TIMESTEP_SEC)

            // Extract simulated position
            val simTransform = robotBody.transform
            val simX = simTransform.translationX
            val simY = simTransform.translationY
            val simHeading = simTransform.rotationAngle

            // Update Robot State
            state = state.copy(
                timestampMs = System.currentTimeMillis(),
                drive = state.drive.copy(
                    odometryX = simX,
                    odometryY = simY,
                    odometryHeading = simHeading
                )
            )

            // Publish to AdvantageScope
            TelemetryPublisher.publish(state)

            // Loop timing
            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = TIMESTEP_MS - elapsed
            if (sleepTime > 0) {
                Thread.sleep(sleepTime)
            }
        }
    }

    private fun createWalls(world: World<Body>) {
        // Center is (0,0), so walls are at +/- 1.825
        val halfWidth = FIELD_WIDTH / 2.0
        
        val topWall = Body()
        topWall.addFixture(Geometry.createRectangle(FIELD_WIDTH, 0.1))
        topWall.setMass(MassType.INFINITE)
        topWall.translate(0.0, halfWidth)
        world.addBody(topWall)

        val bottomWall = Body()
        bottomWall.addFixture(Geometry.createRectangle(FIELD_WIDTH, 0.1))
        bottomWall.setMass(MassType.INFINITE)
        bottomWall.translate(0.0, -halfWidth)
        world.addBody(bottomWall)

        val leftWall = Body()
        leftWall.addFixture(Geometry.createRectangle(0.1, FIELD_HEIGHT))
        leftWall.setMass(MassType.INFINITE)
        leftWall.translate(-halfWidth, 0.0)
        world.addBody(leftWall)

        val rightWall = Body()
        rightWall.addFixture(Geometry.createRectangle(0.1, FIELD_HEIGHT))
        rightWall.setMass(MassType.INFINITE)
        rightWall.translate(halfWidth, 0.0)
        world.addBody(rightWall)
    }
}
