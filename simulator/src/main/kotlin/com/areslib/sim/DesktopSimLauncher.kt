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
        val robotFixture = robotBody.addFixture(Geometry.createRectangle(0.45, 0.45))
        // 35 lbs ~ 15.875 kg. Area = 0.45 * 0.45 = 0.2025. Density = 15.875 / 0.2025 = 78.395
        robotFixture.density = 78.395
        robotBody.setMass(MassType.NORMAL)
        // Spawn robot in the exact center of the field (0, 0)
        robotBody.translate(0.0, 0.0)
        world.addBody(robotBody)

        // Add Game Pieces (Decode balls: 5in diameter -> 0.0635m radius, 0.165lbs -> 0.075kg)
        val balls = mutableListOf<Body>()
        for (i in 0..2) {
            val ball = Body()
            val fixture = ball.addFixture(Geometry.createCircle(0.0635))
            fixture.friction = 0.6
            fixture.restitution = 0.4 // Moderate bounce
            // Mass = 0.075 kg. Area = pi * 0.0635^2 = 0.012667. Density = 0.075 / 0.012667 = 5.92
            fixture.density = 5.92
            ball.setMass(MassType.NORMAL)
            ball.linearDamping = 2.0 // Carpet friction for balls
            ball.angularDamping = 2.0
            // Spawn balls somewhat randomly in front of the robot
            ball.translate(1.0, -1.0 + i * 1.0)
            world.addBody(ball)
            balls.add(ball)
        }

        // Add walls (static bodies)
        createWalls(world)

        println("Simulation Running at 50Hz. Press Ctrl+C to stop.")

        // 4. DIGITAL TWIN WIRING (Mock Hardware Layer)
        val robotDouble = SwerveRobotDouble()
        val octoquad = com.areslib.ftc.hardware.OctoQuadFWv3(robotDouble.octoQuadI2c).apply { initialize() }
        val srsHub = com.areslib.ftc.hardware.SrsHubDriver(robotDouble.srsHubI2c).apply { initialize() }
        
        val driveEncoders = Array(4) { i -> com.areslib.ftc.hardware.OctoQuadEncoderIO(octoquad, i) }
        val steerEncoders = Array(4) { i -> com.areslib.ftc.hardware.OctoQuadAbsolutePWMEncoder(octoquad, 4 + i) }
        
        val intakeEncoder = com.areslib.ftc.hardware.SrsHubEncoderIO(srsHub, 0)
        val transferEncoder = com.areslib.ftc.hardware.SrsHubEncoderIO(srsHub, 1)
        val shooterEncoder = com.areslib.ftc.hardware.SrsHubEncoderIO(srsHub, 2)
        val floodgateAnalog = com.areslib.ftc.hardware.SrsHubAnalogIO(srsHub, 3)
        val floodgateCurrentSensor = com.areslib.ftc.hardware.FtcFloodgateCurrentSensor(floodgateAnalog)
        
        val kinematics = com.areslib.kinematics.SwerveKinematics(
            com.areslib.math.Translation2d(0.225, 0.225),
            com.areslib.math.Translation2d(0.225, -0.225),
            com.areslib.math.Translation2d(-0.225, 0.225),
            com.areslib.math.Translation2d(-0.225, -0.225)
        )

        // 5. Simulation Loop
        var state = RobotState()
        var currentDistance = 0.0
        var lastDistance = 0.0
        var lastShootTime = 0L
        
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
                if (driverStation.isFieldCentric) {
                    com.areslib.math.ChassisSpeeds.fromFieldRelativeSpeeds(
                        speeds.vxMetersPerSecond,
                        speeds.vyMetersPerSecond,
                        speeds.omegaRadiansPerSecond,
                        currentPose.heading
                    )
                } else {
                    speeds
                }
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

            // --- PHYSICAL ACTUATOR COMMAND CLOSED LOOP ---
            // --- PHYSICAL ACTUATOR COMMAND CLOSED LOOP ---
            // Translate requested speeds to wheel modules
            val targetStates = kinematics.toSwerveModuleStates(chassisSpeeds)
            for (i in 0 until 4) {
                robotDouble.drivePowers[i] = targetStates[i].speedMetersPerSecond / 4.0
                
                // CR Servo steering closed-loop logic (proportional steer angle correction)
                val steerErrorRaw = targetStates[i].angle.radians - robotDouble.steerAngles[i]
                var steerError = (steerErrorRaw + kotlin.math.PI) % (2.0 * kotlin.math.PI)
                if (steerError < 0.0) steerError += 2.0 * kotlin.math.PI
                steerError -= kotlin.math.PI
                robotDouble.steerPowers[i] = (steerError * 4.0).coerceIn(-1.0, 1.0)
            }
            
            // Wire superstructure buttons to hardware double
            robotDouble.intakePower = if (driverStation.isIntaking) 1.0 else 0.0
            robotDouble.transferPower = if (driverStation.isIntaking) 0.8 else 0.0
            robotDouble.shooterPower = if (driverStation.isShooting) 1.0 else 0.0
            robotDouble.hasBallInIntake = driverStation.isIntaking && state.superstructure.inventoryCount < 3
            robotDouble.hasBallInTransfer = state.superstructure.inventoryCount > 0

            // Step the digital twin physics forward by 20ms
            robotDouble.update(TIMESTEP_SEC)

            // Read sensors back from virtual registers using production drivers
            srsHub.update()
            floodgateCurrentSensor.update()

            // --- FORWARD KINEMATICS FOR DYN4J RIGID BODY DRIVE ---
            // Calculate actual physical velocities from drive wheel and steer module encoders
            var vcx: Double = 0.0
            var vcy: Double = 0.0
            var omega: Double = 0.0
            
            for (i in 0 until 4) {
                // Ticks/sec to meters/sec
                val moduleVel: Double = driveEncoders[i].velocity / (2048.0 / (2.0 * kotlin.math.PI * 0.05))
                val moduleAngle: Double = robotDouble.steerAngles[i] // From the absolute steer feedback
                
                val vxMod: Double = moduleVel * kotlin.math.cos(moduleAngle)
                val vyMod: Double = moduleVel * kotlin.math.sin(moduleAngle)
                
                vcx += vxMod
                vcy += vyMod
                
                val rx: Double = robotDouble.moduleX[i]
                val ry: Double = robotDouble.moduleY[i]
                
                omega += (-vxMod * ry + vyMod * rx) / (rx * rx + ry * ry)
            }
            
            vcx /= 4.0
            vcy /= 4.0
            omega /= 4.0

            // Convert Robot-Relative Chassis velocities to World-Relative Velocities for Dyn4j
            val heading: Double = currentPose.heading.radians
            val worldVx = vcx * kotlin.math.cos(heading) - vcy * kotlin.math.sin(heading)
            val worldVy = vcx * kotlin.math.sin(heading) + vcy * kotlin.math.cos(heading)

            // Wake up the physics body
            robotBody.isAtRest = false

            // Apply forces to simulate traction/momentum
            val kpLinear = 50.0
            val kpAngular = 20.0
            val forceX = (worldVx - robotBody.linearVelocity.x) * kpLinear
            val forceY = (worldVy - robotBody.linearVelocity.y) * kpLinear
            val torque = (omega - robotBody.angularVelocity) * kpAngular

            robotBody.applyForce(org.dyn4j.geometry.Vector2(forceX, forceY))
            robotBody.applyTorque(torque)

            // Print highly detailed current and thermal warning metrics periodically
            if (System.currentTimeMillis() % 1000 < 20) {
                println(String.format(
                    "| FLOODGATE LOAD | Current: %.2f A | Temp: %.1f%% | Energy: %.4f Wh | Fuse Alert: %b |",
                    floodgateCurrentSensor.current,
                    floodgateCurrentSensor.fuseThermalLoadPercent,
                    floodgateCurrentSensor.estimatedEnergyWattHours,
                    floodgateCurrentSensor.isOverloadWarning()
                ))
                println(String.format(
                    "| SUPERSTRUCTURE | Intake: %.0f | Transfer: %.0f | Shooter: %.0f |",
                    intakeEncoder.position,
                    transferEncoder.position,
                    shooterEncoder.position
                ))
            }

            // FSM state updates
            if (driverStation.isTeleopMode) {
                state = com.areslib.reducer.rootReducer(
                    state, 
                    com.areslib.action.RobotAction.SetIntakeActive(driverStation.isIntaking, System.currentTimeMillis())
                )
                
                // Intake Logic (Distance-based sensor approximation)
                if (state.superstructure.intakeActive && state.superstructure.inventoryCount < 3) {
                    val iterator = balls.iterator()
                    while (iterator.hasNext()) {
                        val ball = iterator.next()
                        val dist = kotlin.math.hypot(
                            ball.transform.translationX - robotBody.transform.translationX,
                            ball.transform.translationY - robotBody.transform.translationY
                        )
                        // If within 30cm (intake range)
                        if (dist < 0.3) {
                            world.removeBody(ball)
                            iterator.remove()
                            state = com.areslib.reducer.rootReducer(
                                state, 
                                com.areslib.action.RobotAction.SetInventoryCount(state.superstructure.inventoryCount + 1, System.currentTimeMillis())
                            )
                            println("INTAKED BALL! Inventory: ${state.superstructure.inventoryCount}")
                        }
                    }
                }

                // Shoot Logic
                if (driverStation.isShooting && state.superstructure.inventoryCount > 0 && System.currentTimeMillis() - lastShootTime > 500) {
                    lastShootTime = System.currentTimeMillis()
                    state = com.areslib.reducer.rootReducer(
                        state, 
                        com.areslib.action.RobotAction.SetInventoryCount(state.superstructure.inventoryCount - 1, System.currentTimeMillis())
                    )
                    
                    val ball = Body()
                    val fixture = ball.addFixture(Geometry.createCircle(0.0635))
                    fixture.friction = 0.6
                    fixture.restitution = 0.4
                    // Mass = 0.075 kg. Area = pi * 0.0635^2 = 0.012667. Density = 0.075 / 0.012667 = 5.92
                    fixture.density = 5.92
                    ball.setMass(MassType.NORMAL)
                    ball.linearDamping = 2.0
                    ball.angularDamping = 2.0
                    
                    // Spawn slightly in front of robot
                    val spawnDist = 0.35
                    val spawnX = currentPose.x + cos(currentPose.heading.radians) * spawnDist
                    val spawnY = currentPose.y + sin(currentPose.heading.radians) * spawnDist
                    ball.translate(spawnX, spawnY)
                    
                    // Apply strong impulse forward
                    val shootVelocity = 15.0 // m/s
                    val impulseX = cos(currentPose.heading.radians) * shootVelocity * ball.mass.mass
                    val impulseY = sin(currentPose.heading.radians) * shootVelocity * ball.mass.mass
                    ball.applyImpulse(org.dyn4j.geometry.Vector2(impulseX, impulseY))
                    
                    world.addBody(ball)
                    balls.add(ball)
                    println("SHOT BALL! Inventory: ${state.superstructure.inventoryCount}")
                }
            }

            // Step physics engine
            world.step(1, TIMESTEP_SEC)

            // Extract new simulated position after step
            val newTransform = robotBody.transform
            var newX = newTransform.translationX
            var newY = newTransform.translationY
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
            
            // Publish Game Pieces (AdvantageScope expects 7 doubles per Pose3d)
            val gamePieceData = DoubleArray(balls.size * 7)
            for (i in balls.indices) {
                gamePieceData[i * 7] = balls[i].transform.translationX
                gamePieceData[i * 7 + 1] = balls[i].transform.translationY
                gamePieceData[i * 7 + 2] = 0.0635 // Z height (radius)
                
                val theta = balls[i].transform.rotationAngle
                gamePieceData[i * 7 + 3] = kotlin.math.cos(theta / 2.0) // qW
                gamePieceData[i * 7 + 4] = 0.0                          // qX
                gamePieceData[i * 7 + 5] = 0.0                          // qY
                gamePieceData[i * 7 + 6] = kotlin.math.sin(theta / 2.0) // qZ
            }
            TelemetryPublisher.publishGamePieces(gamePieceData)

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
