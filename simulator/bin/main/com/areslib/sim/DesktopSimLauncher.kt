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

        // 2. Setup Virtual Driver Station
        val driverStation = VirtualDriverStation()
        driverStation.isVisible = true

        // 3. Setup Controllers & Trajectory
        val mockJson = """
            {
              "waypoints": [
                {
                  "anchor": {"x": -1.5, "y": -1.0},
                  "nextControl": {"x": -0.5, "y": -1.0}
                },
                {
                  "anchor": {"x": 0.0, "y": 0.0},
                  "prevControl": {"x": -0.5, "y": 0.0},
                  "nextControl": {"x": 0.5, "y": 0.0}
                },
                {
                  "anchor": {"x": 1.5, "y": 1.0},
                  "prevControl": {"x": 0.5, "y": 1.0}
                }
              ],
              "eventMarkers": [
                {
                  "name": "IntakeOn",
                  "waypointRelativePos": 1.5,
                  "command": {
                    "type": "named",
                    "name": "IntakeOn"
                  }
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
        var lastDistance = 0.0
        
        while (true) {
            val startTime = System.currentTimeMillis()

            // Current Simulated Pose
            val simTransform = robotBody.transform
            val currentPose = Pose2d(
                x = simTransform.translationX,
                y = simTransform.translationY,
                heading = Rotation2d(simTransform.rotationAngle)
            )

            // Determine ChassisSpeeds based on mode
            val chassisSpeeds = if (driverStation.isTeleopMode) {
                // Publish current pose to TargetPose so AdvantageScope layouts mapped to the ghost robot still update
                TelemetryPublisher.publishTargetPose(currentPose)
                
                val speeds = driverStation.getChassisSpeeds()
                if (speeds.vxMetersPerSecond != 0.0 || speeds.vyMetersPerSecond != 0.0 || speeds.omegaRadiansPerSecond != 0.0) {
                    println("TELEOP INPUT: vx=${speeds.vxMetersPerSecond}, vy=${speeds.vyMetersPerSecond}, omega=${speeds.omegaRadiansPerSecond}")
                }
                speeds
            } else {
                // Calculate Target State
                val tempState = path.sampleAtDistance(currentDistance)
                // Only kickstart if we are at the very beginning of the path
                val simStepVelocity = if (currentDistance < 0.01 && tempState.velocityMps < 0.1) 0.1 else tempState.velocityMps
                
                lastDistance = currentDistance
                currentDistance += simStepVelocity * TIMESTEP_SEC
                val targetState = path.sampleAtDistance(currentDistance)
                
                // Check for triggered events
                path.events.forEach { event ->
                    if (lastDistance < event.triggerDistanceMeters && currentDistance >= event.triggerDistanceMeters) {
                        println(">>> EVENT TRIGGERED: ${event.eventName} at distance ${event.triggerDistanceMeters}")
                    }
                }

                TelemetryPublisher.publishTargetPose(targetState.pose)

                driveController.calculate(
                    currentPose,
                    targetState.pose,
                    targetState.velocityMps,
                    targetState.pose.heading,
                    TIMESTEP_SEC
                )
            }

            // Convert Robot-Relative ChassisSpeeds to World-Relative Velocities for Dyn4j
            val heading = currentPose.heading.radians
            var worldVx = 0.0
            var worldVy = 0.0

            if (driverStation.isTeleopMode && driverStation.isFieldCentric) {
                // Field-centric: WASD directly map to world coordinates
                var fieldX = chassisSpeeds.vxMetersPerSecond
                var fieldY = chassisSpeeds.vyMetersPerSecond
                
                // If red alliance, invert controls since driver stands on opposite side of field
                if (driverStation.isRedAlliance) {
                    fieldX = -fieldX
                    fieldY = -fieldY
                }
                worldVx = fieldX
                worldVy = fieldY
            } else {
                // Robot-centric: Rotate WASD vectors by current robot heading
                worldVx = chassisSpeeds.vxMetersPerSecond * cos(heading) - chassisSpeeds.vyMetersPerSecond * sin(heading)
                worldVy = chassisSpeeds.vxMetersPerSecond * sin(heading) + chassisSpeeds.vyMetersPerSecond * cos(heading)
            }

            // Wake up the physics body (in case it went to sleep at the end of the path)
            robotBody.isAtRest = false

            // Apply velocities
            robotBody.linearVelocity = org.dyn4j.geometry.Vector2(worldVx, worldVy)
            robotBody.angularVelocity = chassisSpeeds.omegaRadiansPerSecond

            // Step physics engine
            world.step(1, TIMESTEP_SEC)

            // Extract new simulated position after step
            val newTransform = robotBody.transform
            var newX = newTransform.translationX
            var newY = newTransform.translationY
            val newHeading = newTransform.rotationAngle

            // Enforce virtual field boundary (robot half-width is 0.225)
            val boundaryLimit = (FIELD_WIDTH / 2.0) - 0.225
            var clamped = false
            if (newX > boundaryLimit) { newX = boundaryLimit; clamped = true }
            if (newX < -boundaryLimit) { newX = -boundaryLimit; clamped = true }
            if (newY > boundaryLimit) { newY = boundaryLimit; clamped = true }
            if (newY < -boundaryLimit) { newY = -boundaryLimit; clamped = true }

            if (clamped) {
                robotBody.transform.setTranslation(newX, newY)
            }

            // Update Robot State
            state = state.copy(
                timestampMs = System.currentTimeMillis(),
                drive = state.drive.copy(
                    odometryX = newX,
                    odometryY = newY,
                    odometryHeading = newHeading
                )
            )

            if (driverStation.isTeleopMode) {
                // Log pose to see if it's actually changing
                println("TELEOP POSE -> X: $newX, Y: $newY, Heading: $newHeading")
            }

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
