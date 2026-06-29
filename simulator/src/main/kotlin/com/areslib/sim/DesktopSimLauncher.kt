package com.areslib.sim

import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.pathing.PathPlannerParser
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.math.ChassisSpeeds
import com.areslib.state.RobotState
import com.areslib.util.RobotClock
import com.qualcomm.hardware.limelightvision.LLResult
import com.qualcomm.hardware.limelightvision.LLResultTypes
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
        println("Starting ARESLib Desktop Simulation (HIL-style actual FTC code)...")

        var activeConfig: com.areslib.state.RobotFieldConfig? = null
        var watchFieldConfig = false
        var fieldConfigArg: String? = null
        var replayCloudId: String? = null
        var headless = false

        var argIdx = 0
        while (argIdx < args.size) {
            when (args[argIdx]) {
                "--field-config" -> {
                    if (argIdx + 1 < args.size) {
                        fieldConfigArg = args[argIdx + 1]
                        argIdx++
                    }
                }
                "--watch" -> {
                    watchFieldConfig = true
                }
                "--headless" -> {
                    headless = true
                }
                "--replay-cloud" -> {
                    if (argIdx + 1 < args.size) {
                        replayCloudId = args[argIdx + 1]
                        argIdx++
                    }
                }
            }
            argIdx++
        }

        fun loadConfigContent(arg: String): String? {
            val file = java.io.File(arg)
            if (file.exists()) {
                return file.readText()
            }
            val envBaseUrl = System.getenv("ARESWEB_API_URL") ?: System.getProperty("aresweb.api.url")
            val baseUrl = envBaseUrl ?: "http://localhost:5001/aresfirst-portal/us-central1/api"
            return try {
                val url = java.net.URL("$baseUrl/simulations/field-config/$arg")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                if (code == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    System.err.println("Failed to fetch field config $arg from ARESWEB: HTTP $code")
                    null
                }
            } catch (e: java.lang.Exception) {
                System.err.println("Error fetching field config $arg: ${e.message}")
                null
            }
        }

        if (fieldConfigArg != null) {
            val content = loadConfigContent(fieldConfigArg)
            if (content != null) {
                try {
                    val gson = com.google.gson.Gson()
                    activeConfig = gson.fromJson(content, com.areslib.state.RobotFieldConfig::class.java)
                    if (activeConfig != null) {
                        println("[Simulator] Successfully loaded field config: ${activeConfig.name}")
                        com.areslib.state.RobotFieldManager.setActiveConfig(activeConfig)
                    }
                } catch (e: java.lang.Exception) {
                    System.err.println("Failed to parse loaded field config: ${e.message}")
                }
            }
        }

        // Read EKF config overrides if present
        var customVisionStdDevs: com.areslib.math.Vector3? = null
        var configFile = java.io.File("config_override.json")
        if (!configFile.exists()) {
            val parentFile = java.io.File("../config_override.json")
            if (parentFile.exists()) {
                configFile = parentFile
            }
        }
        if (configFile.exists()) {
            try {
                val configContent = configFile.readText()
                val gson = com.google.gson.Gson()
                val configMap = gson.fromJson(configContent, Map::class.java)
                val overrideX = (configMap["visionStdDevX"] as? Number)?.toDouble()
                val overrideY = (configMap["visionStdDevY"] as? Number)?.toDouble()
                val overrideTheta = (configMap["visionStdDevTheta"] as? Number)?.toDouble()
                if (overrideX != null && overrideY != null && overrideTheta != null) {
                    customVisionStdDevs = com.areslib.math.Vector3(overrideX, overrideY, overrideTheta)
                    println("[Simulator Config] Loaded EKF Standard Deviation overrides: X=$overrideX, Y=$overrideY, Theta=$overrideTheta")
                }
            } catch (e: Exception) {
                println("[Simulator Config] Failed to parse config_override.json: ${e.message}")
            }
        }

        if (replayCloudId != null) {
            println("[Simulator] Replaying cloud run $replayCloudId...")
            try {
                TelemetryPublisher.javaClass // Make sure NT4 is initialized
                val nt4Telemetry = com.areslib.telemetry.NT4Telemetry()
                val publisher = com.areslib.telemetry.ReplayPublisher(nt4Telemetry)
                val summary = com.areslib.logging.CloudReplayProvider.loadRun(replayCloudId, customVisionStdDevs)
                println("[Simulator] Fetched cloud run summary: ${summary.steps.size} steps. Streaming to NetworkTables...")
                publisher.publishReplay(summary)
                println("[Simulator] Cloud replay completed.")
            } catch (e: Exception) {
                System.err.println("Failed to replay cloud run: ${e.message}")
            }
            return
        }

        // 1. Initialize WPILib Telemetry
        println("Initializing Telemetry (NT4 & DataLog)...")
        TelemetryPublisher.javaClass

        // 2. Setup Virtual Driver Station
        val driverStation = VirtualDriverStation()
        if (headless) {
            driverStation.isTeleopMode = false
        } else {
            driverStation.isVisible = true
        }

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
        val xController = PIDController(p = 10.0, i = 0.0, d = 0.4)
        val yController = PIDController(p = 10.0, i = 0.0, d = 0.4)
        val thetaController = PIDController(p = 3.0, i = 0.0, d = 0.2)
        val driveController = HolonomicDriveController(xController, yController, thetaController)

        // 3. Setup Dyn4j World
        val world = World<Body>()
        world.setGravity(org.dyn4j.geometry.Vector2(0.0, 0.0)) // Top-down 2D view, no gravity

        // Create the Robot Body
        val robotBody = Body()
        // Standard FTC robot footprint (18x18 inches ~ 0.45x0.45 meters)
        val robotFixture = robotBody.addFixture(Geometry.createRectangle(0.45, 0.45))
        robotFixture.density = 15.0 // 15kg
        robotBody.setMass(MassType.NORMAL)
        
        // Spawn configuration (SQUARE_INVERTED starts RED at x = -1.8, headings 0.0)
        val startPose = Pose2d(-1.8, 0.0, Rotation2d(0.0))
        robotBody.translate(startPose.x, startPose.y)
        robotBody.rotate(startPose.heading.radians)
        world.addBody(robotBody)

        createWalls(world)
        val activeObstacles = mutableListOf<Body>()
        val balls = mutableListOf<Body>()

        // Load obstacles and game pieces from field configuration
        if (activeConfig != null) {
            val obstacles = FieldObstacleLoader.loadObstacles(world, activeConfig.obstacles)
            activeObstacles.addAll(obstacles)
            val elements = FieldElementLoader.loadElements(world, activeConfig.elementTypes, activeConfig.elements)
            balls.addAll(elements)
            NT4FieldPublisher.publishConfigId(activeConfig.id)
            NT4FieldPublisher.publishObstacles(activeConfig.obstacles)
        } else {
            // Load standard FTC obstacles & game pieces
            var obstaclesFile: java.io.File? = null
            val paths = listOf(
                "src/main/assets/paths/obstacles.json",
                "TeamCode/src/main/assets/paths/obstacles.json",
                "src/main/deploy/paths/obstacles.json",
                "frc-app/src/main/deploy/paths/obstacles.json"
            )
            for (p in paths) {
                val f = java.io.File(p)
                if (f.exists()) {
                    obstaclesFile = f
                    break
                }
            }
            if (obstaclesFile != null) {
                try {
                    val content = obstaclesFile.readText()
                    val obstacles = FieldObstacleLoader.loadObstaclesFromAnalyticsJson(content)
                    val loaded = FieldObstacleLoader.loadObstacles(world, obstacles)
                    activeObstacles.addAll(loaded)
                    NT4FieldPublisher.publishObstacles(obstacles)
                } catch (e: Exception) {
                    println("Failed to load initial field obstacles: ${e.message}")
                }
            }
        }

        println("Simulation Running at 50Hz. Press Ctrl+C to stop.")

        // 4. Mecanum Robot Double & OpMode Thread setup
        val robotDouble = MecanumRobotDouble()
        val opMode = org.firstinspires.ftc.teamcode.opmodes.ARESMecanumTeleOp()
        opMode.hardwareMap = robotDouble.hardwareMap

        // Start student's OpMode in the background
        val opModeThread = Thread {
            try {
                opMode.runOpMode()
            } catch (e: Exception) {
                System.err.println("ARESMecanumTeleOp Thread terminated: ${e.message}")
                e.printStackTrace()
            }
        }.apply {
            isDaemon = true
            start()
        }

        // Wait 1.5 seconds for OpMode's pre-match initialization loop to run
        val initStartTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - initStartTime < 1500) {
            // Step simulator sensors at origin so EKF aligns
            robotDouble.updateSensors(TIMESTEP_SEC, 0.0, 0.0, 0.0, startPose.x, startPose.y, startPose.heading.radians)
            Thread.sleep(20)
        }
        
        // Match Start! Exits init loop in LinearOpMode
        opMode.isStarted = true
        println("[Simulator] Driver clicked PLAY! Activating telemetry & drivetrain controls.")

        // 5. Simulation Loop
        var state = RobotState()
        var currentDistance = 0.0
        var lastDistance = 0.0
        var lastShootTime = 0L
        val headlessRecords = mutableListOf<HeadlessRecord>()
        var settlingTicks = 0
        
        var lastX = startPose.x
        var lastY = startPose.y
        var lastHeading = startPose.heading.radians
        var simLoopCount = 0
        val activeFieldConfig = com.areslib.state.RobotFieldManager.activeConfig
        val visionTags = if (activeFieldConfig.apriltags.isNotEmpty()) {
            activeFieldConfig.apriltags.associate { tag ->
                tag.id to com.areslib.math.Pose3d(
                    com.areslib.math.Translation3d(tag.x, tag.y, tag.z),
                    com.areslib.math.Rotation3d(0.0, 0.0, Math.toRadians(tag.yaw))
                )
            }
        } else {
            mapOf(
                1 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(1.8, 1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, Math.PI)),
                2 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(1.8, -1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, Math.PI)),
                3 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(-1.8, 1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, 0.0)),
                4 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(-1.8, -1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, 0.0))
            )
        }
        var lastObstaclesTimestamp = 0L

        // FSM telemetry properties
        var simFlywheelRPM = 0.0
        var simIntakeActive = false
        var simFlywheelActive = false
        var simTransferActive = false
        var simInventoryCount = 0

        while (true) {
            if (!headless) {
                org.lwjgl.glfw.GLFW.glfwPollEvents()
            }
            TelemetryPublisher.pollWebInputs(driverStation)

            val startTime = RobotClock.currentTimeMillis()

            // Current Simulated Pose
            val simTransform = robotBody.transform
            val currentPose = Pose2d(
                x = simTransform.translationX,
                y = simTransform.translationY,
                heading = Rotation2d(simTransform.rotationAngle)
            )

            // Determine ChassisSpeeds based on mode
            val chassisSpeeds = if (driverStation.isTeleopMode) {
                // Map keyboard/dashboard inputs to gamepad1 fields
                val driveSpeeds = driverStation.getChassisSpeeds()
                
                // Map stick deflection to scale [-1.0f, 1.0f]
                opMode.gamepad1.left_stick_y = -(driveSpeeds.vxMetersPerSecond / 4.0).toFloat()
                opMode.gamepad1.left_stick_x = -(driveSpeeds.vyMetersPerSecond / 4.0).toFloat()
                opMode.gamepad1.right_stick_x = -(driveSpeeds.omegaRadiansPerSecond / 4.0).toFloat()
                
                // Map auxiliary toggles to standard gamepad buttons
                opMode.gamepad1.b = driverStation.isFieldCentric // B triggers AprilTag align or similar
                opMode.gamepad1.left_bumper = driverStation.isIntaking
                opMode.gamepad1.right_bumper = driverStation.isFlywheelOn
                opMode.gamepad1.right_trigger = if (driverStation.isTransferring) 1.0f else 0.0f
                opMode.gamepad1.y = false // Triangle resets EKF

                // Read motor powers set by the running OpMode
                val pFL = robotDouble.fl.power
                val pFR = robotDouble.fr.power
                val pBL = robotDouble.bl.power
                val pBR = robotDouble.br.power

                // Translate wheel powers to chassis-space velocities
                val maxWheelSpeed = 3.5
                val wFL = pFL * maxWheelSpeed
                val wFR = pFR * maxWheelSpeed
                val wBL = pBL * maxWheelSpeed
                val wBR = pBR * maxWheelSpeed

                val L = 0.45
                val vx = (wFL + wFR + wBL + wBR) / 4.0
                val vy = (-wFL + wFR + wBL - wBR) / 4.0
                val omega = (-wFL + wFR - wBL + wBR) / (4.0 * L)

                TelemetryPublisher.publishTargetPose(currentPose)

                ChassisSpeeds(vx, vy, omega)
            } else {
                // Auto Path Follower calculations
                val tempState = path.sampleAtDistance(currentDistance)
                val simStepVelocity = if (currentDistance < 0.01 && tempState.velocityMps < 0.1) 0.1 else tempState.velocityMps
                
                lastDistance = currentDistance
                currentDistance += simStepVelocity * TIMESTEP_SEC
                val targetState = path.sampleAtDistance(currentDistance)

                TelemetryPublisher.publishTargetPose(targetState.pose)

                val totalDistance = path.points.lastOrNull()?.distanceMeters ?: 0.0
                val nextSampleDistance = kotlin.math.min(currentDistance + 0.05, totalDistance)
                val nextState = path.sampleAtDistance(nextSampleDistance)
                val tangentHeading = if (nextSampleDistance - currentDistance > 1e-4) {
                    val dx = nextState.pose.x - targetState.pose.x
                    val dy = nextState.pose.y - targetState.pose.y
                    if (kotlin.math.hypot(dx, dy) > 1e-4) {
                        Rotation2d(kotlin.math.atan2(dy, dx))
                    } else {
                        targetState.pose.heading
                    }
                } else {
                    targetState.pose.heading
                }

                driveController.calculate(
                    currentPose,
                    Pose2d(targetState.pose.x, targetState.pose.y, tangentHeading),
                    targetState.velocityMps,
                    targetState.pose.heading,
                    TIMESTEP_SEC
                )
            }

            // Raycast virtual AprilTag 1 relative coordinates if visible
            val tag1 = visionTags[1]
            if (tag1 != null) {
                val dx = tag1.translation.x - currentPose.x
                val dy = tag1.translation.y - currentPose.y
                val dist = kotlin.math.hypot(dx, dy)
                
                // Visible if in front of tag (x < 1.8) and within range (3m)
                if (currentPose.x < 1.8 && dist < 3.0) {
                    val targetSpaceX = dy // X+ is tag-right
                    val targetSpaceZ = 1.8 - currentPose.x // Z+ is tag-outward
                    val targetSpaceYaw = currentPose.heading.radians - Math.PI
                    
                    val mockOrientation = org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles(
                        org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.RADIANS,
                        0.0, targetSpaceYaw, 0.0
                    )
                    val mockPosition = org.firstinspires.ftc.robotcore.external.navigation.Position(
                        org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.METER,
                        targetSpaceX, 0.0, targetSpaceZ, 0
                    )
                    val mockPose = org.firstinspires.ftc.robotcore.external.navigation.Pose3D(mockPosition, mockOrientation)
                    
                    val fiducialResult = LLResultTypes.FiducialResult(
                        1, 0.0, 0.0, org.firstinspires.ftc.robotcore.external.navigation.Pose3D(), mockPose
                    )
                    robotDouble.limelight.setLatestResult(SimLLResult(valid = true, fiducials = listOf(fiducialResult)))
                } else {
                    robotDouble.limelight.setLatestResult(null)
                }
            }

            // Step simulated encoder feedback registries
            robotDouble.updateSensors(
                TIMESTEP_SEC,
                chassisSpeeds.vxMetersPerSecond,
                chassisSpeeds.vyMetersPerSecond,
                chassisSpeeds.omegaRadiansPerSecond,
                currentPose.x,
                currentPose.y,
                currentPose.heading.radians
            )

            // Dynamic FSM simulation values
            simIntakeActive = driverStation.isIntaking
            simFlywheelActive = driverStation.isFlywheelOn
            if (simFlywheelActive) {
                simFlywheelRPM = simFlywheelRPM + (4000.0 - simFlywheelRPM) * 5.0 * TIMESTEP_SEC
            } else {
                simFlywheelRPM = simFlywheelRPM + (0.0 - simFlywheelRPM) * 2.0 * TIMESTEP_SEC
            }
            simTransferActive = driverStation.isTransferring && simFlywheelRPM >= 3800.0

            // Intake ball collection logic
            if (simIntakeActive && simInventoryCount < 3) {
                val iterator = balls.iterator()
                while (iterator.hasNext()) {
                    val ball = iterator.next()
                    val dist = kotlin.math.hypot(
                        ball.transform.translationX - robotBody.transform.translationX,
                        ball.transform.translationY - robotBody.transform.translationY
                    )
                    if (dist < 0.3) {
                        world.removeBody(ball)
                        iterator.remove()
                        simInventoryCount++
                        println("INTAKED BALL! Inventory: $simInventoryCount")
                    }
                }
            }

            // Shooting spawning logic
            if (simTransferActive && simInventoryCount > 0 && RobotClock.currentTimeMillis() - lastShootTime > 500) {
                lastShootTime = RobotClock.currentTimeMillis()
                simInventoryCount--
                
                val ball = Body()
                val fixture = ball.addFixture(Geometry.createCircle(0.0635))
                fixture.friction = 0.6
                fixture.restitution = 0.4
                fixture.density = 5.92
                ball.setMass(MassType.NORMAL)
                ball.linearDamping = 2.0
                ball.angularDamping = 2.0
                
                val spawnDist = 0.35
                val spawnX = currentPose.x + cos(currentPose.heading.radians) * spawnDist
                val spawnY = currentPose.y + sin(currentPose.heading.radians) * spawnDist
                ball.translate(spawnX, spawnY)
                
                val shootVelocity = (simFlywheelRPM / 4000.0) * 15.0
                val impulseX = cos(currentPose.heading.radians) * shootVelocity * ball.mass.mass
                val impulseY = sin(currentPose.heading.radians) * shootVelocity * ball.mass.mass
                ball.applyImpulse(org.dyn4j.geometry.Vector2(impulseX, impulseY))
                
                world.addBody(ball)
                balls.add(ball)
                println("SHOT BALL! RPM: %.0f | Inventory: $simInventoryCount".format(simFlywheelRPM))
            }

            // --- DRIVE DYN4J BODY FROM CHASSIS SPEEDS ---
            val heading = currentPose.heading.radians
            val worldVx = chassisSpeeds.vxMetersPerSecond * cos(heading) - chassisSpeeds.vyMetersPerSecond * sin(heading)
            val worldVy = chassisSpeeds.vxMetersPerSecond * sin(heading) + chassisSpeeds.vyMetersPerSecond * cos(heading)

            robotBody.isAtRest = false
            val velocity = robotBody.linearVelocity
            velocity.x = velocity.x + (worldVx - velocity.x) * 12.0 * TIMESTEP_SEC
            velocity.y = velocity.y + (worldVy - velocity.y) * 12.0 * TIMESTEP_SEC
            robotBody.linearVelocity = velocity
            robotBody.angularVelocity = chassisSpeeds.omegaRadiansPerSecond

            // Apply forces to body
            val kpLinear = 150.0
            val kpAngular = 20.0
            val forceX = (worldVx - robotBody.linearVelocity.x) * kpLinear
            val forceY = (worldVy - robotBody.linearVelocity.y) * kpLinear
            val torque = (chassisSpeeds.omegaRadiansPerSecond - robotBody.angularVelocity) * kpAngular
            robotBody.applyForce(org.dyn4j.geometry.Vector2(forceX, forceY))
            robotBody.applyTorque(torque)

            // Step physics engine
            world.step(1, TIMESTEP_SEC)

            // Synchronize RobotState telemetry values
            state = RobotState().copy(
                drive = state.drive.copy(
                    poseEstimator = state.drive.poseEstimator.copy(
                        estimatedPose = currentPose
                    )
                ),
                superstructure = state.superstructure.copy(
                    intakeActive = simIntakeActive,
                    flywheelActive = simFlywheelActive,
                    flywheelRPM = simFlywheelRPM,
                    transferActive = simTransferActive,
                    inventoryCount = simInventoryCount
                )
            )

            // AdvantageScope outputs
            TelemetryPublisher.publishChassisSpeeds(chassisSpeeds)
            TelemetryPublisher.publishDriveMode(driverStation.isFieldCentric, driverStation.isTeleopMode, driverStation.isRedAlliance)
            TelemetryPublisher.publishEstimatedPose(currentPose)
            TelemetryPublisher.publish(state)

            val gamePieceData = DoubleArray(balls.size * 7)
            for (i in balls.indices) {
                gamePieceData[i * 7] = balls[i].transform.translationX
                gamePieceData[i * 7 + 1] = balls[i].transform.translationY
                gamePieceData[i * 7 + 2] = 0.0635
                val theta = balls[i].transform.rotationAngle
                gamePieceData[i * 7 + 3] = cos(theta / 2.0)
                gamePieceData[i * 7 + 4] = 0.0
                gamePieceData[i * 7 + 5] = 0.0
                gamePieceData[i * 7 + 6] = sin(theta / 2.0)
            }
            TelemetryPublisher.publishGamePieces(gamePieceData)

            val dynamicElementPoses = balls.mapIndexed { idx, ball ->
                val id = (ball.userData as? String) ?: "ball_$idx"
                DynamicElementPose(
                    id = id,
                    x = ball.transform.translationX,
                    y = ball.transform.translationY,
                    rotation = Math.toDegrees(ball.transform.rotationAngle)
                )
            }
            NT4FieldPublisher.publishElements(dynamicElementPoses)

            // Loop timing
            val elapsed = RobotClock.currentTimeMillis() - startTime
            val sleepTime = TIMESTEP_MS - elapsed
            if (sleepTime > 0) {
                Thread.sleep(sleepTime)
            }
        }
    }

    private fun createWalls(world: World<Body>) {
        val halfWidth = 3.6576 / 2.0
        addWall(world, 0.0, halfWidth, 3.6576, 0.1)   // Top
        addWall(world, 0.0, -halfWidth, 3.6576, 0.1)  // Bottom
        addWall(world, -halfWidth, 0.0, 0.1, 3.6576)  // Left
        addWall(world, halfWidth, 0.0, 0.1, 3.6576)   // Right
    }

    private fun addWall(world: World<Body>, x: Double, y: Double, w: Double, h: Double) {
        val wall = Body()
        wall.addFixture(Geometry.createRectangle(w, h))
        wall.setMass(MassType.INFINITE)
        wall.translate(x, y)
        world.addBody(wall)
    }

    data class HeadlessRecord(
        val timeMs: Long,
        val targetX: Double,
        val targetY: Double,
        val targetHeading: Double,
        val actualX: Double,
        val actualY: Double,
        val actualHeading: Double,
        val deviationM: Double
    )
}
