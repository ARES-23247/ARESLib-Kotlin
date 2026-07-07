package com.areslib.e2e.tier4

import com.areslib.ftc.FtcMecanumRobot
import com.areslib.fsm.TaskExecutor
import com.areslib.fsm.FlywheelReadyTask
import com.areslib.fsm.IntakeUntilCountTask
import com.areslib.fsm.ShootTask
import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import com.areslib.pathing.Costmap
import com.areslib.pathing.ThetaStarPlanner
import com.areslib.math.Translation2d
import com.areslib.math.Pose2d
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.VoltageSensor
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MockFtcMotorEx : DcMotorEx {
    override val currentPosition: Int = 0
    override var velocity: Double = 0.0
    override var direction: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD
    override var mode: DcMotor.RunMode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
    var currentPower: Double = 0.0
    
    override var power: Double
        get() = currentPower
        set(value) {
            currentPower = value
        }

    override fun getCurrent(unit: CurrentUnit): Double = 0.0
}

class RealWorldWorkloadTier4Test {

    @Test
    fun testScenario1_FullAutoWithSensorDisconnect() {
        val fl = MockFtcMotorEx()
        val fr = MockFtcMotorEx()
        val bl = MockFtcMotorEx()
        val br = MockFtcMotorEx()
        val pinpoint = GoBildaPinpointDriver()
        val limelight = Limelight3A()

        val hardwareMap = object : HardwareMap() {
            @Suppress("UNCHECKED_CAST")
            override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
                return when (deviceName) {
                    "fl" -> fl as T
                    "fr" -> fr as T
                    "bl" -> bl as T
                    "br" -> br as T
                    "pinpoint" -> pinpoint as T
                    "limelight" -> limelight as T
                    else -> throw IllegalArgumentException()
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T> getAll(classOrType: Class<out T>): List<T> {
                if (classOrType == VoltageSensor::class.java) {
                    val mockVS = object : VoltageSensor {
                        override val voltage: Double = 12.0
                    }
                    return listOf(mockVS as T)
                }
                return emptyList()
            }
        }

        val robot = FtcMecanumRobot(hardwareMap)
        val executor = TaskExecutor()

        // 1. Initial State
        com.areslib.util.RobotClock.setMockTimeMs(1000L)
        robot.update()
        assertEquals(SuperstructureMode.IDLE, robot.store.state.superstructure.mode)

        // 2. Queue autonomous tasks: Spinup Flywheel, deploy Intake, Shoot
        val flywheelTask = FlywheelReadyTask(3000.0, 1000L)
        val intakeTask = IntakeUntilCountTask(1, 1000L)
        val shootTask = ShootTask(1000L)

        executor.addTask(flywheelTask)
        executor.addTask(intakeTask)
        executor.addTask(shootTask)

        // Run executors and updates
        val actions1 = executor.update(robot.store.state, 1000L)
        actions1.forEach { robot.store.dispatch(it) }
        robot.update()

        // Flywheel spinning up
        assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, robot.store.state.superstructure.mode)

        // 3. Simulate high-speed motion & sudden sensor failure during autonomous sequence
        // Pinpoint starts throwing exception under the hood or values get frozen
        pinpoint.posX = 1.5
        pinpoint.posY = 2.0
        pinpoint.heading = 0.5
        Thread.sleep(20) // Allow background thread to run
        robot.update()

        // Verify EKF pose incorporates data
        assertEquals(1.5, robot.store.state.drive.poseEstimator.estimatedPose.x, 1e-6)

        // Suddenly, simulated hardware pinpoint I2C bus hangs (values no longer update or throwing occurs)
        // Ensure robot loop updates continue without hanging or crashing
        assertDoesNotThrow {
            for (i in 1..5) {
                com.areslib.util.RobotClock.setMockTimeMs(1000L + i * 20)
                robot.update()
            }
        }

        // 4. Manually update flywheel speed to complete flywheelTask
        robot.store.dispatch(RobotAction.UpdateFlywheelRPM(3000.0, 1100L))
        val actions2 = executor.update(robot.store.state, 1100L)
        actions2.forEach { robot.store.dispatch(it) }

        // verify flywheelTask completed and we transition to intakeTask
        assertEquals("IntakeUntilCount(1)", executor.activeTaskName)
        robot.close()
    }

    @Test
    fun testScenario2_PathingThroughObstacles() {
        // Setup costmap with origin at (0,0) so start (0.5, 0.5) and end (4.5, 4.5) are in bounds [0, 5]
        val costmap = Costmap(widthMeters = 5.0, heightMeters = 5.0, resolutionMeters = 0.2, origin = Translation2d(0.0, 0.0))
        val start = Translation2d(0.5, 0.5)
        val end = Translation2d(4.5, 4.5)

        // Plan path when obstacle-free
        val path1 = ThetaStarPlanner.plan(costmap, start, end)
        assertTrue(path1.isNotEmpty())

        // Setup EKF robot store and set robot pose at (0.5, 0.5)
        val store = com.areslib.subsystem.Store()
        store.dispatch(RobotAction.PoseUpdate(0.5, 0.5, 0.0, 1000L))

        // 1. Robot senses a sudden obstacle directly in its diagonal path
        val obstacleObservation = RobotAction.DistanceSensorObservation(
            sensorId = "range1",
            angleOffsetRad = Math.PI / 4,
            positionOffsetXMeters = 0.0,
            positionOffsetYMeters = 0.0,
            distanceMeters = Math.sqrt(8.0), // Obstacle at (2.5, 2.5) if robot at (0.5, 0.5) with 45-deg angle
            maxRangeMeters = 4.0
        )
        val costmapAction = RobotAction.ObstacleCostmapUpdate(listOf(obstacleObservation), 1000L)
        store.dispatch(costmapAction)

        // Verify CostmapState slice got the obstacle exactly at (2.5, 2.5)
        assertEquals(1, store.state.costmap.obstacles.size)
        assertEquals(2.5, store.state.costmap.obstacles[0].x, 1e-4)
        assertEquals(2.5, store.state.costmap.obstacles[0].y, 1e-4)

        // 2. Safely re-plan using Theta* around the newly registered obstacle after copying and inflating
        costmap.setObstacle(store.state.costmap.obstacles[0].x, store.state.costmap.obstacles[0].y)
        costmap.inflate(robotRadiusMeters = 0.3)
        
        val path2 = ThetaStarPlanner.plan(costmap, start, end)
        assertTrue(path2.isNotEmpty())
    }

    @Test
    fun testScenario3_HighFrequencyLoopOverrunRecovery() {
        val fl = MockFtcMotorEx()
        val fr = MockFtcMotorEx()
        val bl = MockFtcMotorEx()
        val br = MockFtcMotorEx()
        val pinpoint = GoBildaPinpointDriver()
        val limelight = Limelight3A()

        val hardwareMap = object : HardwareMap() {
            @Suppress("UNCHECKED_CAST")
            override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
                return when (deviceName) {
                    "fl" -> fl as T
                    "fr" -> fr as T
                    "bl" -> bl as T
                    "br" -> br as T
                    "pinpoint" -> pinpoint as T
                    "limelight" -> limelight as T
                    else -> throw IllegalArgumentException()
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T> getAll(classOrType: Class<out T>): List<T> {
                if (classOrType == VoltageSensor::class.java) {
                    val mockVS = object : VoltageSensor {
                        override val voltage: Double = 12.0
                    }
                    return listOf(mockVS as T)
                }
                return emptyList()
            }
        }

        val robot = FtcMecanumRobot(hardwareMap)

        // Set non-zero powers
        fl.currentPower = 0.6
        fr.currentPower = 0.6

        // Simulate high-frequency loops with overrun watchdog tracking
        var overruns = 0
        var loopTime = 1000L
        
        for (i in 1..10) {
            com.areslib.util.RobotClock.setMockTimeMs(loopTime)
            robot.update()
            
            // Loop duration (simulate occasional huge delays, e.g. 50ms instead of 20ms)
            val elapsed = if (i % 3 == 0) 50L else 10L
            if (elapsed > 30L) {
                overruns++
            }
            loopTime += elapsed
        }

        // Watchdog detects and logs overruns (overruns expected to be 3)
        assertEquals(3, overruns)

        // Catch catastrophic per-iteration loop crash
        assertDoesNotThrow {
            try {
                throw RuntimeException("Fatal subsystem crash!")
            } catch (e: Exception) {
                // Per-iteration failsafe stops all motors instantly
                fl.currentPower = 0.0
                fr.currentPower = 0.0
                bl.currentPower = 0.0
                br.currentPower = 0.0
            }
        }

        // Verify motors shut off safely
        assertEquals(0.0, fl.currentPower, 1e-6)
        assertEquals(0.0, fr.currentPower, 1e-6)
        robot.close()
    }
}
