package com.areslib.sim

import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import com.areslib.sim.cli.SimCliParser
import com.areslib.sim.field.FieldElementLoader
import com.areslib.sim.field.MecanumInteractionModel
import com.areslib.sim.infra.VirtualDriverStation
import com.areslib.sim.model.MecanumRobotDouble
import com.areslib.sim.network.NT4FieldPublisher
import com.areslib.sim.network.TelemetryPublisher
import com.areslib.sim.opmode.SimOpModeRunner
import com.areslib.sim.physics.SimPhysicsWorld
import com.areslib.sim.replay.SimReplayEngine
import com.areslib.state.RobotFieldManager
import com.areslib.util.RobotClock
import java.io.File

/**
 * Object implementation for Desktop Sim Launcher.
 *
 * Robotics framework control component.
 */
object DesktopSimLauncher {
    private const val TIMESTEP_SEC = 0.01


    @Volatile private var sumSqErrorX = 0.0
    @Volatile private var sumSqErrorY = 0.0
    @Volatile private var sumSqErrorHeading = 0.0
    @Volatile private var maxCurrent = 0.0
    @Volatile private var sampleCount = 0L

    @JvmStatic
    /**
     * main declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
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

    /**
     * launch declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun launch(
        args: Array<String>,
        interactionModel: SimInteractionModel,
        opModeArg: com.qualcomm.robotcore.eventloop.opmode.LinearOpMode? = null
    ) {
        println("Starting ARESLib Desktop Simulation...")
        RobotClock.useMockTime(0L)

        sumSqErrorX = 0.0
        sumSqErrorY = 0.0
        sumSqErrorHeading = 0.0
        maxCurrent = 0.0
        sampleCount = 0L

        Runtime.getRuntime().addShutdownHook(Thread {
            RobotClock.useSystemTime()
            val count = sampleCount
            if (count > 0) {
                val rmseX = kotlin.math.sqrt(sumSqErrorX / count)
                val rmseY = kotlin.math.sqrt(sumSqErrorY / count)
                val rmseHeading = kotlin.math.sqrt(sumSqErrorHeading / count)
                val summaryJson = String.format(
                    "{\n  \"rmseX\": %.5f,\n  \"rmseY\": %.5f,\n  \"rmseHeading\": %.5f,\n  \"maxCurrentAmps\": %.3f,\n  \"sampleCount\": %d\n}",
                    rmseX, rmseY, rmseHeading, maxCurrent, count
                )
                try {
                    val summaryFile = File("ares_run_summary.json")
                    summaryFile.writeText(summaryJson)
                    println("[Simulator] Wrote run summary to ${summaryFile.absolutePath}")
                } catch (e: Exception) {
                    System.err.println("Failed to write simulation run summary: ${e.message}")
                }
            }
        })

        // 1. CLI Parsing
        val cliArgs = SimCliParser.parseArgs(args)
        val activeConfig = SimCliParser.loadFieldConfig(cliArgs.fieldConfigArg)
        val customVisionStdDevs = SimCliParser.loadEkfOverrides()

        if (cliArgs.replayCloudId != null) {
            SimReplayEngine.replayCloudRun(cliArgs.replayCloudId, customVisionStdDevs)
            return
        }

        // 2. Telemetry & Web Server Initialization
        println("Initializing Telemetry (NT4 & DataLog)...")
        TelemetryPublisher.javaClass
        com.areslib.logging.LogManagerServer.startServer()

        val serverMode = opModeArg == null && cliArgs.opModeClassName == null
        val driverStation = VirtualDriverStation()
        if (!cliArgs.headless && !serverMode) {
            driverStation.isVisible = true
        }

        if (serverMode) {
            println("[Simulator] Running in Driver Station Server Mode")
            SimOpModeRunner.scanAndPublishOpModes()
        }

        // 3. Dyn4j Physics World Initialization
        val physicsWorld = SimPhysicsWorld()
        var startPose = physicsWorld.setupSpawnPose(driverStation.isRedAlliance)
        physicsWorld.loadFieldElements(activeConfig)

        // 4. Mecanum Robot Double & OpMode Execution
        val robotDouble = MecanumRobotDouble()
        var activeInteractionModel = interactionModel
        if (activeInteractionModel is NoOpInteractionModel) {
            activeInteractionModel = MecanumInteractionModel(robotDouble)
        }

        val syncRobotPoseToPhysics = {
            val currentPhysPose = Pose2d(
                physicsWorld.robotBody.transform.translationX,
                physicsWorld.robotBody.transform.translationY,
                Rotation2d(physicsWorld.robotBody.transform.rotationAngle)
            )
            com.areslib.ftc.FtcBaseRobot.activeInstance?.let { robotInstance ->
                val now = RobotClock.currentTimeMillis()
                robotInstance.pinpointIO?.initialize(
                    Pose2d(currentPhysPose.x, currentPhysPose.y, Rotation2d(currentPhysPose.heading.radians)),
                    resetHardware = true
                )
                robotInstance.store.dispatch(
                    com.areslib.action.RobotAction.PoseUpdate(
                        xMeters = currentPhysPose.x,
                        yMeters = currentPhysPose.y,
                        headingRadians = currentPhysPose.heading.radians,
                        timestampMs = now,
                        isReset = true
                    )
                )
                println("[Simulator] Automatically synced robot EKF and Pinpoint to physics starting pose: $currentPhysPose")
            }
            Unit
        }

        var activeOpMode = SimOpModeRunner.createOpModeInstance(opModeArg, cliArgs.opModeClassName)

        val startOpMode = { opModeToRun: com.qualcomm.robotcore.eventloop.opmode.LinearOpMode ->
            opModeToRun.hardwareMap = robotDouble.hardwareMap
            Thread {
                try {
                    opModeToRun.runOpMode()
                } catch (_: InterruptedException) {
                } catch (e: Exception) {
                    System.err.println("OpMode Thread terminated: ${e.message}")
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        if (activeOpMode != null) {
            startOpMode(activeOpMode)

            val initStartTime = RobotClock.currentTimeMillis()
            while (RobotClock.currentTimeMillis() - initStartTime < 1500) {
                val ccwPos = com.areslib.ftc.FtcBaseRobot.activeInstance?.pinpointIsCcwPositive ?: true
                robotDouble.updateSensors(TIMESTEP_SEC, 0.0, 0.0, 0.0, startPose.x, startPose.y, startPose.heading.radians, ccwPos)
                if (RobotClock.isMocked) {
                    RobotClock.useMockTime(RobotClock.currentTimeMillis() + 20)
                }
                Thread.sleep(20)
            }

            println("[Simulator] Driver clicked PLAY! Activating telemetry & drivetrain controls.")
            syncRobotPoseToPhysics()
            activeOpMode.isStarted = true
        }

        println("Simulation Running at 50Hz. Press Ctrl+C to stop.")

        val ntInst = org.frcforftc.networktables.NetworkTablesInstance.getDefaultInstance()
        var lastDsCommand = ""
        var lastSelectedOpMode = ""

        while (true) {
            // Check for Driver Station UI commands from ARES-Analytics dashboard
            val rawCommand = (ntInst.get("/ARES/DriverStation/Command")?.value?.get() as? String)
                ?: (ntInst.get("ARES/DriverStation/Command")?.value?.get() as? String)
                ?: ""
            val dsCommand = rawCommand.trim()

            val rawOpMode = (ntInst.get("/ARES/DriverStation/SelectedOpMode")?.value?.get() as? String)
                ?: (ntInst.get("ARES/DriverStation/SelectedOpMode")?.value?.get() as? String)
                ?: ""
            val selectedOpMode = rawOpMode.trim()


            if (selectedOpMode.isNotEmpty() && selectedOpMode != lastSelectedOpMode) {
                lastSelectedOpMode = selectedOpMode
                println("[Simulator] Driver Station selected OpMode: $selectedOpMode")
            }

            if (dsCommand.isNotEmpty() && dsCommand != lastDsCommand) {
                lastDsCommand = dsCommand
                println("[Simulator] Driver Station command received: $dsCommand")
                when (dsCommand) {
                    "INIT" -> {
                        try {
                            activeOpMode?.isStopRequested = true
                            val newOpMode = SimOpModeRunner.createOpModeInstance(null, lastSelectedOpMode)
                                ?: com.areslib.ftc.hardware.AresHardwareTestOpMode()
                            activeOpMode = newOpMode
                            startOpMode(newOpMode)
                            println("[Simulator] Successfully INITED OpMode: ${newOpMode.javaClass.simpleName}")
                        } catch (e: Exception) {
                            System.err.println("[Simulator] Failed to INIT OpMode: ${e.message}")
                        }
                    }
                    "START" -> {
                        activeOpMode?.let { mode ->
                            syncRobotPoseToPhysics()
                            mode.isStarted = true
                            println("[Simulator] OpMode STARTED.")
                        }
                    }
                    "STOP" -> {
                        activeOpMode?.isStopRequested = true
                        robotDouble.fl.power = 0.0
                        robotDouble.fr.power = 0.0
                        robotDouble.rl.power = 0.0
                        robotDouble.rr.power = 0.0
                        println("[Simulator] OpMode STOPPED.")
                    }
                }
            }

            val ccwPos = com.areslib.ftc.FtcBaseRobot.activeInstance?.pinpointIsCcwPositive ?: true
            val currentPhysPose = Pose2d(
                physicsWorld.robotBody.transform.translationX,
                physicsWorld.robotBody.transform.translationY,
                Rotation2d(physicsWorld.robotBody.transform.rotationAngle)
            )

            // Drive Dyn4j physics body from simulated motor powers
            val flP = robotDouble.fl.power
            val frP = robotDouble.fr.power
            val rlP = robotDouble.rl.power
            val rrP = robotDouble.rr.power

            val rawVx = (flP + frP + rlP + rrP) / 4.0 * 2.6
            val rawVy = (-flP + frP + rlP - rrP) / 4.0 * 2.6
            val rawOmega = (-flP + frP - rlP + rrP) / 4.0 * 3.5

            val heading = currentPhysPose.heading.radians
            val cosH = kotlin.math.cos(heading)
            val sinH = kotlin.math.sin(heading)

            val targetFieldVx = rawVx * cosH - rawVy * sinH
            val targetFieldVy = rawVx * sinH + rawVy * cosH

            val curVx = physicsWorld.robotBody.linearVelocity.x
            val curVy = physicsWorld.robotBody.linearVelocity.y
            val curOmega = physicsWorld.robotBody.angularVelocity

            val maxLinearStep = 8.0 * TIMESTEP_SEC
            val maxAngularStep = 15.0 * TIMESTEP_SEC

            val newVx = curVx + (targetFieldVx - curVx).coerceIn(-maxLinearStep, maxLinearStep)
            val newVy = curVy + (targetFieldVy - curVy).coerceIn(-maxLinearStep, maxLinearStep)
            val newOmega = curOmega + (rawOmega - curOmega).coerceIn(-maxAngularStep, maxAngularStep)

            physicsWorld.robotBody.setAtRest(false)
            physicsWorld.robotBody.linearVelocity = org.dyn4j.geometry.Vector2(newVx, newVy)
            physicsWorld.robotBody.angularVelocity = newOmega

            physicsWorld.world.step(1)

            val vx = physicsWorld.robotBody.linearVelocity.x
            val vy = physicsWorld.robotBody.linearVelocity.y
            val omega = physicsWorld.robotBody.angularVelocity
            robotDouble.updateSensors(TIMESTEP_SEC, vx, vy, omega, currentPhysPose.x, currentPhysPose.y, currentPhysPose.heading.radians, ccwPos)

            // Stream dynamic game piece positions to NT4 for live visual rendering
            val pieces = physicsWorld.gamePieces
            if (pieces.isNotEmpty()) {
                val arr = DoubleArray(pieces.size * 7)
                for (i in pieces.indices) {
                    val p = pieces[i]
                    val base = i * 7
                    arr[base + 0] = p.transform.translationX
                    arr[base + 1] = p.transform.translationY
                    arr[base + 2] = p.transform.rotationAngle
                    arr[base + 3] = 0.15
                    arr[base + 4] = 0.15
                    arr[base + 5] = 0.0
                    arr[base + 6] = 0.0
                }
                TelemetryPublisher.publishGamePieces(arr)
            }

            TelemetryPublisher.publishTruePose(currentPhysPose)
            TelemetryPublisher.getWebVx()
            TelemetryPublisher.getWebVy()
            TelemetryPublisher.getWebOmega()

            val activeInstance = com.areslib.ftc.FtcBaseRobot.activeInstance
            if (activeInstance != null) {
                val state = activeInstance.store.state
                TelemetryPublisher.publish(state)
                
                val ekfPose = state.drive.poseEstimator.estimatedPose
                TelemetryPublisher.publishTargetPose(ekfPose)

                ntInst.putNumber("Hardware/Motors/fl/Power", robotDouble.fl.power)
                ntInst.putNumber("Hardware/Motors/fr/Power", robotDouble.fr.power)
                ntInst.putNumber("Hardware/Motors/rl/Power", robotDouble.rl.power)
                ntInst.putNumber("Hardware/Motors/rr/Power", robotDouble.rr.power)

                ntInst.putNumber("Hardware/Motors/fl/Velocity", robotDouble.fl.velocity)
                ntInst.putNumber("Hardware/Motors/fr/Velocity", robotDouble.fr.velocity)
                ntInst.putNumber("Hardware/Motors/rl/Velocity", robotDouble.rl.velocity)
                ntInst.putNumber("Hardware/Motors/rr/Velocity", robotDouble.rr.velocity)
            }

            if (RobotClock.isMocked) {
                RobotClock.useMockTime(RobotClock.currentTimeMillis() + 10)
            }
            try {
                Thread.sleep(10)
            } catch (_: InterruptedException) {
                break
            }

        }
    }
}
