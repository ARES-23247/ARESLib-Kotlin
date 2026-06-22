package com.areslib.fsm

import com.areslib.action.RobotAction
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.pathing.Path
import com.areslib.pathing.PathEvent
import com.areslib.pathing.PathPoint
import com.areslib.pathing.HolonomicPathFollower
import com.areslib.reducer.rootReducer
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import com.areslib.subsystem.AresRobot
import com.areslib.subsystem.DrivetrainSubsystem
import com.areslib.subsystem.Store
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AutonomousDXTest {

    private fun createMockFollower(): HolonomicPathFollower {
        val mockDrivetrain = object : DrivetrainSubsystem {
            var pose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
            override fun setChassisSpeeds(vx: Double, vy: Double, omega: Double) {}
            override fun getEstimatedPose(): Pose2d = pose
            override fun readSensors(store: Store, timestampMs: Long) {}
            override fun writeOutputs(state: RobotState, scale: Double) {}
        }
        return HolonomicPathFollower(mockDrivetrain)
    }

    @Test
    fun testEmbeddedEventMarkers() {
        com.areslib.util.RobotClock.useMockTime(1000L)
        try {
            val store = Store(initialState = RobotState(), reducer = ::rootReducer)
            val follower = createMockFollower()

            // Create a path with two event markers: one at 0.5 meters, one at 1.5 meters
            val path = Path(
                points = listOf(
                    PathPoint(Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 0.0),
                    PathPoint(Pose2d(1.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 1.0),
                    PathPoint(Pose2d(2.0, 0.0, Rotation2d.fromDegrees(0.0)), 1.5, 2.0)
                ),
                events = listOf(
                    PathEvent("IntakeOn", 0.5),
                    PathEvent("IntakeOff", 1.5)
                )
            )

            val task = FollowPathTask(follower, path)
            val taskExecutor = TaskExecutor()
            taskExecutor.addTask(task)

            // Initialize at 1000L
            val initActions = taskExecutor.update(store.state, 1000L)
            initActions.forEach { store.dispatch(it) }

            // Initial path state check
            assertNotNull(store.state.pathState.activePath)
            assertTrue(store.state.pathState.currentDistanceMeters < 0.05)
            assertFalse(store.state.superstructure.intakeActive)

            // Step 1: Simulate progress to 0.4 meters (no events should trigger yet)
            store.dispatch(RobotAction.UpdatePathProgress(0.4, 1020L))
            com.areslib.util.RobotClock.useMockTime(1020L)
            val actions1 = taskExecutor.update(store.state, 1020L)
            actions1.forEach { store.dispatch(it) }
            assertFalse(store.state.superstructure.intakeActive)

            // Step 2: Simulate progress to 0.49 meters.
            // FollowPathTask's execute will calculate nextDistance based on velocity.
            // targetPoint velocity is 1.5 m/s. At dt = 0.02s, progress increases by 0.03m.
            // nextDistance = 0.49 + 0.03 = 0.52m, which is >= 0.5m.
            store.dispatch(RobotAction.UpdatePathProgress(0.49, 1040L))
            com.areslib.util.RobotClock.useMockTime(1040L)
            val actions2 = taskExecutor.update(store.state, 1040L)
            actions2.forEach { store.dispatch(it) }
            
            // The IntakeOn event should have triggered and transitioned superstructure mode to INTAKING
            assertTrue(store.state.superstructure.intakeActive)
            assertEquals(SuperstructureMode.INTAKING, store.state.superstructure.mode)

            // Step 3: Drive past 1.5 meters.
            // nextDistance = 1.49 + 0.03 = 1.52m, which is >= 1.5m.
            store.dispatch(RobotAction.UpdatePathProgress(1.49, 1060L))
            com.areslib.util.RobotClock.useMockTime(1060L)
            val actions3 = taskExecutor.update(store.state, 1060L)
            actions3.forEach { store.dispatch(it) }

            // IntakeOff event at 1.5 should trigger, turning intake off
            assertFalse(store.state.superstructure.intakeActive)
            assertEquals(SuperstructureMode.IDLE, store.state.superstructure.mode)
        } finally {
            com.areslib.util.RobotClock.useSystemTime()
        }
    }

    @Test
    fun testRobotSequenceFluentBuilder() {
        val follower = createMockFollower()
        val path = Path(emptyList(), emptyList())

        val compositeTask = RobotSequence()
            .followPath(path, follower)
            .waitDistance(1.2)
            .waitTime(500L)
            .dispatch(RobotAction.SetInventoryCount(2, 1000L))
            .build()

        assertTrue(compositeTask is SequentialTaskGroup)
        assertEquals("Sequential(FollowPath(0 points), PathProgressWait(1.2 m), TimeWait(500 ms), ActionDispatch(SetInventoryCount))", compositeTask.name)
    }

    @Test
    fun testSubsystemCommands() {
        val robot = AresRobot()
        
        assertNotNull(robot.superstructure)
        assertEquals(SuperstructureMode.IDLE, robot.superstructure.mode)

        // Create tasks using factories
        val flywheelTask = robot.superstructure.flywheelReadyCommand(3000.0, 1000L)
        val intakeTask = robot.superstructure.intakeUntilCountCommand(1, 1000L)
        val shootTask = robot.superstructure.shootCommand(1000L)

        assertEquals("FlywheelReady(3000.0 RPM)", flywheelTask.name)
        assertEquals("IntakeUntilCount(1)", intakeTask.name)
        assertEquals("Shoot", shootTask.name)
    }

    @Test
    fun testConfigAutoParser() {
        val follower = createMockFollower()
        val jsonString = """
            {
                "name": "Test Script",
                "steps": [
                    { "type": "spinflywheel", "rpm": 3500.0 },
                    { "type": "shoot" },
                    { "type": "waitdistance", "meters": 0.8 },
                    { "type": "waittime", "durationMs": 400 },
                    { "type": "dispatchpathevent", "eventName": "IntakeOn" }
                ]
            }
        """.trimIndent()

        val compiledTask = ConfigAutoParser.parse(jsonString, follower, 1000L)
        assertTrue(compiledTask is SequentialTaskGroup)
        assertEquals("Sequential(FlywheelReady(3500.0 RPM), Shoot, PathProgressWait(0.8 m), TimeWait(400 ms), ActionDispatch(PathEventTriggered))", compiledTask.name)
    }
}
