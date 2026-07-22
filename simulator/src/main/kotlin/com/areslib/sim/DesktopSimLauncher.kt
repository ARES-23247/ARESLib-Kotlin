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

        val activeOpMode = SimOpModeRunner.createOpModeInstance(opModeArg, cliArgs.opModeClassName)
        if (activeOpMode != null) {
            activeOpMode.hardwareMap = robotDouble.hardwareMap

            Thread {
                try {
                    activeOpMode.runOpMode()
                } catch (_: InterruptedException) {
                } catch (e: Exception) {
                    System.err.println("OpMode Thread terminated: ${e.message}")
                    e.printStackTrace()
                }
            }.apply {
                isDaemon = true
                start()
            }

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
    }
}
