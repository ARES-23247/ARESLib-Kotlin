package com.areslib.sim

import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.pathing.PathPlannerParser
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.state.RobotState
import org.dyn4j.dynamics.Body
import org.dyn4j.world.World
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import kotlin.math.cos
import kotlin.math.sin

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

        // 2. Setup Controllers & Trajectory
        val mockJson = """
            {
              "waypoints": [
                {
                  "anchor": {"x": 0.0, "y": 0.0},
                  "nextControl": {"x": 1.0, "y": 0.0}
                },
                {
                  "anchor": {"x": 2.0, "y": 1.5},
                  "prevControl": {"x": 1.0, "y": 1.5},
                  "nextControl": {"x": 3.0, "y": 1.5}
                },
                {
                  "anchor": {"x": 4.0, "y": 0.0},
                  "prevControl": {"x": 3.0, "y": 0.0}
                }
              ]
            }
        """.trimIndent()
        val path = PathPlannerParser.parsePath(mockJson)
        val xController = PIDController(p = 2.0, i = 0.0, d = 0.1)
        val yController = PIDController(p = 2.0, i = 0.0, d = 0.1)
        val thetaController = PIDController(p = 3.0, i = 0.0, d = 0.2)
        val driveController = HolonomicDriveController(xController, yController, thetaController)

        // 3. Setup Dyn4j World
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

        // 4. Simulation Loop
        var state = RobotState()
        var currentDistance = 0.0
        val targetVelocityMps = 0.8
        
        while (true) {
            val startTime = System.currentTimeMillis()

            // Calculate Target State
            currentDistance += targetVelocityMps * TIMESTEP_SEC
            val targetState = path.sampleAtDistance(currentDistance)

            // Current Simulated Pose
            val simTransform = robotBody.transform
            val currentPose = Pose2d(
                x = simTransform.translationX,
                y = simTransform.translationY,
                heading = Rotation2d(simTransform.rotationAngle)
            )

            // Run Controller
            val chassisSpeeds = driveController.calculate(
                currentPose,
                targetState.pose,
                targetVelocityMps,
                targetState.pose.heading,
                TIMESTEP_SEC
            )

            // Convert Robot-Relative ChassisSpeeds to World-Relative Velocities for Dyn4j
            val heading = currentPose.heading.radians
            val worldVx = chassisSpeeds.vxMetersPerSecond * cos(heading) - chassisSpeeds.vyMetersPerSecond * sin(heading)
            val worldVy = chassisSpeeds.vxMetersPerSecond * sin(heading) + chassisSpeeds.vyMetersPerSecond * cos(heading)

            // Apply velocities
            robotBody.linearVelocity = org.dyn4j.geometry.Vector2(worldVx, worldVy)
            robotBody.angularVelocity = chassisSpeeds.omegaRadiansPerSecond

            // Step physics engine
            world.step(1, TIMESTEP_SEC)

            // Extract new simulated position after step
            val newTransform = robotBody.transform
            val newX = newTransform.translationX
            val newY = newTransform.translationY
            val newHeading = newTransform.rotationAngle

            // Update Robot State
            state = state.copy(
                timestampMs = System.currentTimeMillis(),
                drive = state.drive.copy(
                    odometryX = newX,
                    odometryY = newY,
                    odometryHeading = newHeading
                )
            )

            // Publish to AdvantageScope
            TelemetryPublisher.publish(state)
            TelemetryPublisher.publishTargetPose(targetState.pose)

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
