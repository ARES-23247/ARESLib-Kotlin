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
        try {
            launch(args, NoOpInteractionModel())
        } catch (t: Throwable) {
            System.err.println("FATAL CRASH IN SIMULATOR:")
            t.printStackTrace()
            System.err.flush()
            throw t
        }
    }

    fun launch(args: Array<String>, interactionModel: SimInteractionModel, opModeArg: com.qualcomm.robotcore.eventloop.opmode.LinearOpMode? = null) {
        println("Starting ARESLib Desktop Simulation (HIL-style actual FTC code)...")

        var activeConfig: com.areslib.state.RobotFieldConfig? = null
        var watchFieldConfig = false
        var fieldConfigArg: String? = null
        var replayCloudId: String? = null
        var headless = false
        var opModeClassName: String? = null

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
                "--opmode" -> {
                    if (argIdx + 1 < args.size) {
                        opModeClassName = args[argIdx + 1]
                        argIdx++
                    }
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
            val content = loadConfigContent(fieldConfigArg!!)
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
        com.areslib.telemetry.LogManagerServer.startServer()

        // 2. Setup Virtual Driver Station
        val serverMode = opModeArg == null && opModeClassName == null
        val driverStation = VirtualDriverStation()
        if (headless || serverMode) {
            // Headless defaults to teleop mode — dashboard keyboard widget drives via NT4 input topics
        } else {
            driverStation.isVisible = true
        }

        var teleOpsJson = "[]"
        var autosJson = "[]"
        
        if (serverMode) {
            println("[Simulator] Running in Driver Station Server Mode")
            val ntInst = edu.wpi.first.networktables.NetworkTableInstance.getDefault()
            val teleOpTopic = ntInst.getStringTopic("ARES/DriverStation/TeleOpList")
            val teleOpListPub = teleOpTopic.publish()
            teleOpListPub.set("[]")
            teleOpTopic.setRetained(true)
            
            val autoTopic = ntInst.getStringTopic("ARES/DriverStation/AutonomousList")
            val autoListPub = autoTopic.publish()
            autoListPub.set("[]")
            autoTopic.setRetained(true)
            
            try {
                val urls = mutableListOf<java.net.URL>()
                urls.addAll(org.reflections.util.ClasspathHelper.forJavaClassPath())
                urls.addAll(org.reflections.util.ClasspathHelper.forClassLoader(Thread.currentThread().contextClassLoader))
                
                val reflections = org.reflections.Reflections(org.reflections.util.ConfigurationBuilder()
                    .setUrls(urls)
                    .setScanners(org.reflections.scanners.Scanners.TypesAnnotated)
                )
                
                val disabledClass = try {
                    @Suppress("UNCHECKED_CAST")
                    Class.forName("com.qualcomm.robotcore.eventloop.opmode.Disabled") as Class<out Annotation>
                } catch (e: Exception) {
                    null
                }
                
                val disabledOpModes = disabledClass?.let { reflections.getTypesAnnotatedWith(it).map { opMode -> opMode.name }.toSet() } ?: emptySet()
                
                val teleOpClass = try {
                    @Suppress("UNCHECKED_CAST")
                    Class.forName("com.qualcomm.robotcore.eventloop.opmode.TeleOp") as Class<out Annotation>
                } catch (e: Exception) { null }
                
                val autonomousClass = try {
                    @Suppress("UNCHECKED_CAST")
                    Class.forName("com.qualcomm.robotcore.eventloop.opmode.Autonomous") as Class<out Annotation>
                } catch (e: Exception) { null }
                
                val teleops = teleOpClass?.let {
                    reflections.getTypesAnnotatedWith(it)
                        .filter { opMode -> !opMode.name.startsWith("org.firstinspires.ftc.robotcontroller") }
                        .filter { opMode -> disabledClass == null || (!opMode.isAnnotationPresent(disabledClass) && opMode.name !in disabledOpModes) }
                        .map { opMode -> opMode.name }
                } ?: emptyList()
                
                val autos = autonomousClass?.let {
                    reflections.getTypesAnnotatedWith(it)
                        .filter { opMode -> !opMode.name.startsWith("org.firstinspires.ftc.robotcontroller") }
                        .filter { opMode -> disabledClass == null || (!opMode.isAnnotationPresent(disabledClass) && opMode.name !in disabledOpModes) }
                        .map { opMode -> opMode.name }
                } ?: emptyList()
                
                val gson = com.google.gson.Gson()
                teleOpsJson = gson.toJson(teleops)
                autosJson = gson.toJson(autos)
                teleOpListPub.set(teleOpsJson)
                autoListPub.set(autosJson)
                println("[Simulator] Published ${teleops.size} TeleOps and ${autos.size} Autos to NT4")
            } catch (e: Exception) {
                println("[Simulator] Error scanning OpModes: ${e.message}")
            }
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
        
        // Spawn configuration (starts at the center of the field: 0.0, 0.0)
        val startPose = Pose2d(0.0, 0.0, Rotation2d(0.0))
        robotBody.translate(startPose.x, startPose.y)
        robotBody.rotate(startPose.heading.radians)
        world.addBody(robotBody)

        createWalls(world)
        val activeObstacles = mutableListOf<Body>()
        val gamePieces = mutableListOf<Body>()

        // Load obstacles and game pieces from field configuration
        if (activeConfig != null) {
            val obstacles = FieldObstacleLoader.loadObstacles(world, activeConfig.obstacles)
            activeObstacles.addAll(obstacles)
            val elements = FieldElementLoader.loadElements(world, activeConfig.elementTypes, activeConfig.elements)
            gamePieces.addAll(elements)
            NT4FieldPublisher.publishConfigId(activeConfig.id)
            NT4FieldPublisher.publishObstacles(activeConfig.obstacles)
            NT4FieldPublisher.publishAprilTags(activeConfig.apriltags)
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
        
        var loadedAprilTagsJson: String? = null
        if (activeConfig == null) {
            var aprilTagsFile: java.io.File? = null
            val atPaths = listOf(
                "src/main/assets/paths/apriltags.json",
                "TeamCode/src/main/assets/paths/apriltags.json",
                "src/main/deploy/paths/apriltags.json",
                "frc-app/src/main/deploy/paths/apriltags.json"
            )
            for (p in atPaths) {
                val f = java.io.File(p)
                if (f.exists()) {
                    aprilTagsFile = f
                    break
                }
            }
            if (aprilTagsFile != null) {
                try {
                    loadedAprilTagsJson = aprilTagsFile.readText()
                } catch (e: Exception) {
                    println("Failed to load apriltags.json: ${e.message}")
                }
            }
        }

        println("Simulation Running at 50Hz. Press Ctrl+C to stop.")

        // 4. Mecanum Robot Double & OpMode Thread setup
        val robotDouble = MecanumRobotDouble()
        
        var activeOpMode: com.qualcomm.robotcore.eventloop.opmode.LinearOpMode? = null
        var activeOpModeThread: Thread? = null

        if (!serverMode) {
            val opMode = opModeArg ?: Class.forName(opModeClassName).getDeclaredConstructor().newInstance() as com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
            opMode.hardwareMap = robotDouble.hardwareMap
            activeOpMode = opMode
            activeOpModeThread = Thread {
                try {
                    opMode.runOpMode()
                } catch (e: InterruptedException) {
                    // Normal termination
                } catch (e: Exception) {
                    System.err.println("OpMode Thread terminated: ${e.message}")
                    e.printStackTrace()
                }
            }.apply {
                isDaemon = true
                start()
            }

            // Wait 1.5 seconds for OpMode's pre-match initialization loop to run
            val initStartTime = com.areslib.util.RobotClock.currentTimeMillis()
            while (com.areslib.util.RobotClock.currentTimeMillis() - initStartTime < 1500) {
                // Step simulator sensors at origin so EKF aligns
                robotDouble.updateSensors(TIMESTEP_SEC, 0.0, 0.0, 0.0, startPose.x, startPose.y, startPose.heading.radians)
                Thread.sleep(20)
            }
            
            // Match Start! Exits init loop in LinearOpMode
            opMode.isStarted = true
            println("[Simulator] Driver clicked PLAY! Activating telemetry & drivetrain controls.")
        }

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
        } else if (loadedAprilTagsJson != null) {
            try {
                val gson = com.google.gson.Gson()
                val tagsList = gson.fromJson(loadedAprilTagsJson, Array<com.areslib.state.RobotFieldAprilTag>::class.java).toList()
                NT4FieldPublisher.publishAprilTags(tagsList)
                tagsList.associate { tag ->
                    tag.id to com.areslib.math.Pose3d(
                        com.areslib.math.Translation3d(tag.x, tag.y, tag.z),
                        com.areslib.math.Rotation3d(0.0, 0.0, Math.toRadians(tag.yaw))
                    )
                }
            } catch (e: Exception) {
                println("Failed to parse apriltags.json: ${e.message}")
                mapOf(
                    1 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(1.8, 1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, Math.PI)),
                    2 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(1.8, -1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, Math.PI)),
                    3 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(-1.8, 1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, 0.0)),
                    4 to com.areslib.math.Pose3d(com.areslib.math.Translation3d(-1.8, -1.8, 0.5), com.areslib.math.Rotation3d(0.0, 0.0, 0.0))
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
        var gamePieceDataBuffer = DoubleArray(0)
        var lastBallsHash = 0

        // Zero-allocation cached lists for loop
        val visionTagsArray = visionTags.toList().toTypedArray()
        val visibleFiducials = ArrayList<LLResultTypes.FiducialResult>(16)
        val dynamicElementPoses = ArrayList<DynamicElementPose>(64)

        val ntInst = edu.wpi.first.networktables.NetworkTableInstance.getDefault()
        val dsCommandSub = ntInst.getStringTopic("ARES/DriverStation/Command").subscribe("")
        val dsSelectedOpModeSub = ntInst.getStringTopic("ARES/DriverStation/SelectedOpMode").subscribe("")
        val dsTelemetryPub = ntInst.getStringArrayTopic("ARES/DriverStation/Telemetry").publish()
        var lastCommand = ""

        val loopTeleOpPub = ntInst.getStringTopic("ARES/DriverStation/TeleOpList").publish()
        val loopAutoPub = ntInst.getStringTopic("ARES/DriverStation/AutonomousList").publish()

        while (true) {
            val startTime = RobotClock.currentTimeMillis()
            try {
            if (!headless && !serverMode) {
                org.lwjgl.glfw.GLFW.glfwPollEvents()
            }
            TelemetryPublisher.pollWebInputs(driverStation)

            if (serverMode) {
                loopTeleOpPub.set(teleOpsJson)
                loopAutoPub.set(autosJson)
                
                val cmd = dsCommandSub.get()
                if (cmd != lastCommand && cmd.isNotEmpty()) {
                    lastCommand = cmd
                    when (cmd) {
                        "INIT" -> {
                            val selected = dsSelectedOpModeSub.get()
                            if (selected.isNotEmpty()) {
                                println("[Simulator] Received INIT for $selected")
                                activeOpModeThread?.interrupt()
                                activeOpMode?.isStopRequested = true
                                
                                try {
                                    val opMode = Class.forName(selected).getDeclaredConstructor().newInstance() as com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
                                    opMode.hardwareMap = robotDouble.hardwareMap
                                    activeOpMode = opMode
                                    
                                    if (selected.contains("ARESMecanumTeleOp")) {
                                        val initialX = 0.0
                                        val initialHeading = 0.0
                                        robotBody.transform.setTranslation(initialX, 0.0)
                                        robotBody.transform.setRotation(initialHeading)
                                    }

                                    activeOpModeThread = Thread {
                                        try {
                                            opMode.runOpMode()
                                        } catch (e: InterruptedException) {
                                            // normal stop
                                        } catch (e: Exception) {
                                            System.err.println("OpMode Thread terminated: ${e.message}")
                                        }
                                    }.apply {
                                        isDaemon = true
                                        start()
                                    }
                                } catch (e: Exception) {
                                    println("[Simulator] Failed to instantiate OpMode $selected: ${e.message}")
                                }
                            }
                        }
                        "START" -> {
                            println("[Simulator] Received START")
                            activeOpMode?.isStarted = true
                        }
                        "STOP" -> {
                            println("[Simulator] Received STOP")
                            activeOpMode?.isStopRequested = true
                            activeOpModeThread?.interrupt()
                        }
                    }
                }
                
                val mockTelemetry = activeOpMode?.telemetry as? org.firstinspires.ftc.robotcore.external.MockTelemetry
                if (mockTelemetry != null) {
                    val lines = mockTelemetry.displayLines.toTypedArray()
                    if (lines.isNotEmpty()) {
                        dsTelemetryPub.set(lines)
                    }
                }
            }

            // startTime moved to top of loop

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
                activeOpMode?.gamepad1?.left_stick_y = -(driveSpeeds.vxMetersPerSecond / 4.0).toFloat()
                activeOpMode?.gamepad1?.left_stick_x = -(driveSpeeds.vyMetersPerSecond / 4.0).toFloat()
                activeOpMode?.gamepad1?.right_stick_x = -(driveSpeeds.omegaRadiansPerSecond / 4.0).toFloat()
                
                // Buttons
                activeOpMode?.gamepad1?.b = driverStation.isFieldCentric // B triggers AprilTag align or similar
                activeOpMode?.gamepad1?.left_bumper = driverStation.isIntaking
                activeOpMode?.gamepad1?.right_bumper = driverStation.isFlywheelOn
                activeOpMode?.gamepad1?.right_trigger = if (driverStation.isTransferring) 1.0f else 0.0f
                activeOpMode?.gamepad1?.y = false // Triangle resets EKF
                
                // Read motor powers set by the running OpModede
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

            // Raycast all virtual AprilTags
            visibleFiducials.clear()
            for (i in visionTagsArray.indices) {
                val tagId = visionTagsArray[i].first
                val tagPose = visionTagsArray[i].second
                val vecX = currentPose.x - tagPose.translation.x
                val vecY = currentPose.y - tagPose.translation.y
                val dist = kotlin.math.hypot(vecX, vecY)
                
                // Calculate angle from robot to tag to check FOV
                val angleToTag = kotlin.math.atan2(tagPose.translation.y - currentPose.y, tagPose.translation.x - currentPose.x)
                val angleDiff = com.areslib.math.InputMath.wrapAngle(angleToTag - currentPose.heading.radians)
                
                // Calculate Target Space coordinates
                val tagYaw = tagPose.rotation.z
                val targetSpaceX = vecX * -kotlin.math.sin(tagYaw) + vecY * kotlin.math.cos(tagYaw)
                val targetSpaceZ = vecX * kotlin.math.cos(tagYaw) + vecY * kotlin.math.sin(tagYaw)
                val targetSpaceYaw = currentPose.heading.radians - tagYaw

                // Visible if within range (4m) and within FOV (60 degrees => +/- 30 degrees)
                // Also require tag to be facing robot (i.e. targetSpaceZ > 0)
                if (dist < 4.0 && kotlin.math.abs(angleDiff) < Math.toRadians(30.0) && targetSpaceZ > 0.1) {
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
                        tagId, 0.0, 0.0, org.firstinspires.ftc.robotcore.external.navigation.Pose3D(), mockPose
                    )
                    visibleFiducials.add(fiducialResult)
                }
            }

            if (visibleFiducials.isNotEmpty()) {
                robotDouble.limelight.setLatestResult(SimLLResult(valid = true, fiducials = visibleFiducials))
            } else {
                robotDouble.limelight.setLatestResult(null)
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

            // Apply modular interaction model
            simInventoryCount = interactionModel.update(
                world,
                robotBody,
                gamePieces, // (Wait, I need to rename gamePieces to gamePieces across the file first. Let me just use gamePieces here for this chunk and then rename all gamePieces to gamePieces)
                driverStation,
                simInventoryCount,
                currentPose.heading.radians,
                currentPose.x,
                currentPose.y
            )

            // --- DRIVE DYN4J BODY FROM CHASSIS SPEEDS ---
            val heading = currentPose.heading.radians
            val worldVx = chassisSpeeds.vxMetersPerSecond * cos(heading) - chassisSpeeds.vyMetersPerSecond * sin(heading)
            val worldVy = chassisSpeeds.vxMetersPerSecond * sin(heading) + chassisSpeeds.vyMetersPerSecond * cos(heading)

            robotBody.isAtRest = false

            // Apply critically damped forces to body
            val kpLinear = robotBody.mass.mass / TIMESTEP_SEC
            val kpAngular = robotBody.mass.inertia / TIMESTEP_SEC
            val forceX = (worldVx - robotBody.linearVelocity.x) * kpLinear
            val forceY = (worldVy - robotBody.linearVelocity.y) * kpLinear
            val torque = (chassisSpeeds.omegaRadiansPerSecond - robotBody.angularVelocity) * kpAngular
            robotBody.applyForce(org.dyn4j.geometry.Vector2(forceX, forceY))
            robotBody.applyTorque(torque)

            // Step physics engine
            world.step(1, TIMESTEP_SEC)

            // Synchronize RobotState telemetry values
            state = state.copy(
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
            TelemetryPublisher.publishEstimatedPose(state.drive.poseEstimator.estimatedPose)
            TelemetryPublisher.publishTargetPose(currentPose)
            TelemetryPublisher.publish(state)

            var currentBallsHash = gamePieces.size
            for (i in gamePieces.indices) {
                val ball = gamePieces[i]
                val xBits = ball.transform.translationX.toBits()
                val yBits = ball.transform.translationY.toBits()
                currentBallsHash = 31 * currentBallsHash + (xBits xor (xBits ushr 32)).toInt()
                currentBallsHash = 31 * currentBallsHash + (yBits xor (yBits ushr 32)).toInt()
            }

            if (currentBallsHash != lastBallsHash) {
                lastBallsHash = currentBallsHash

                if (gamePieceDataBuffer.size != gamePieces.size * 7) {
                    gamePieceDataBuffer = DoubleArray(gamePieces.size * 7)
                }
                for (i in gamePieces.indices) {
                    gamePieceDataBuffer[i * 7] = gamePieces[i].transform.translationX
                    gamePieceDataBuffer[i * 7 + 1] = gamePieces[i].transform.translationY
                    gamePieceDataBuffer[i * 7 + 2] = 0.0635
                    val theta = gamePieces[i].transform.rotationAngle
                    gamePieceDataBuffer[i * 7 + 3] = cos(theta / 2.0)
                    gamePieceDataBuffer[i * 7 + 4] = 0.0
                    gamePieceDataBuffer[i * 7 + 5] = 0.0
                    gamePieceDataBuffer[i * 7 + 6] = sin(theta / 2.0)
                }
                TelemetryPublisher.publishGamePieces(gamePieceDataBuffer)

                dynamicElementPoses.clear()
                for (idx in gamePieces.indices) {
                    val ball = gamePieces[idx]
                    val id = (ball.userData as? String) ?: "ball_$idx"
                    dynamicElementPoses.add(DynamicElementPose(
                        id = id,
                        x = ball.transform.translationX,
                        y = ball.transform.translationY,
                        rotation = Math.toDegrees(ball.transform.rotationAngle)
                    ))
                }
                NT4FieldPublisher.publishElements(dynamicElementPoses)
            }

            } catch (e: Throwable) {
                System.err.println("Physics watchdog caught exception: ${e.message}")
                e.printStackTrace()
            }

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
