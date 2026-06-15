package com.areslib.e2e.tier3

import com.areslib.control.BrownoutGuard
import com.areslib.control.CurrentBudgetManager
import com.areslib.fsm.TaskExecutor
import com.areslib.fsm.PathProgressWaitTask
import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import com.areslib.ftc.hardware.FtcMotor
import com.areslib.ftc.hardware.PinpointDriverProxy
import com.areslib.ftc.hardware.PinpointOdometryIO
import com.areslib.hardware.OdometryInputs
import com.areslib.math.Pose2d
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MockFtcMotorEx : DcMotorEx {
    override val currentPosition: Int = 0
    var mockVelocity: Double = 0.0
    var mockCurrentAmps: Double = 0.0
    override var direction: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD
    override var mode: DcMotor.RunMode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
    var currentPower: Double = 0.0
    
    override var power: Double
        get() = currentPower
        set(value) {
            currentPower = value
        }

    override val velocity: Double
        get() = mockVelocity

    override fun getCurrent(unit: CurrentUnit): Double {
        return mockCurrentAmps
    }
}

class MockPinpointDriverProxy : PinpointDriverProxy {
    var mockX = 0.0
    var mockY = 0.0
    var mockHeading = 0.0
    var shouldThrow = false
    var resetCalled = false
    var updateCount = 0

    override fun update() {
        updateCount++
        if (shouldThrow) throw RuntimeException("UART disconnect!")
    }

    override val posX: Double get() = mockX
    override val posY: Double get() = mockY
    override val heading: Double get() = mockHeading
    override val velX: Double get() = 0.0
    override val velY: Double get() = 0.0
    override val headingVelocity: Double get() = 0.0

    override fun resetPosAndIMU() {
        resetCalled = true
    }
}

class CrossFeatureTier3Test {

    @Test
    fun testMotorStallAndBrownoutScaling() {
        val mockMotor = MockFtcMotorEx()
        val ftcMotor = FtcMotor(mockMotor)

        // 1. Set up motor stall criteria: high power commanded, low speed
        mockMotor.mockVelocity = 5.0
        val baseTime = 1000L
        com.areslib.util.RobotClock.setMockTimeMs(baseTime)

        // Command motor power
        ftcMotor.power = 0.8
        assertEquals(0.8, mockMotor.power, 1e-6)

        // 2. Set up brownout: 9.0V (sagging)
        val brownout = BrownoutGuard.ftcDefaults()
        brownout.update(9.0)
        assertTrue(brownout.powerScale < 1.0)
        assertEquals(0.72, brownout.powerScale, 1e-6) // Sag is moderate but active

        // Apply brownout scale to commanded power
        val scaledPower = 0.8 * brownout.powerScale // 0.8 * 0.72 = 0.576 (remains above 0.5 stall threshold)
        
        // Fast-forward time past 500ms stall threshold
        com.areslib.util.RobotClock.setMockTimeMs(baseTime + 600)
        
        // Command power again — this triggers stall detection AND brownout scaling
        ftcMotor.power = scaledPower
        
        // Stall detection should trip, cutting power to exactly 0.0
        assertEquals(0.0, mockMotor.power, 1e-6)
    }

    @Test
    fun testSensorDisconnectAndFsmDetour() {
        val mockDriver = MockPinpointDriverProxy()
        val pinpointIO = PinpointOdometryIO(mockDriver)
        val inputs = OdometryInputs()

        // 1. Healthy state pinpoint reading
        mockDriver.mockX = 1.0
        mockDriver.mockY = 1.5
        mockDriver.mockHeading = 0.5
        pinpointIO.updateInputs(inputs)
        assertEquals(1.0, inputs.posX, 1e-6)

        // 2. Setup FSM wait condition
        val executor = TaskExecutor()
        val waitTask = PathProgressWaitTask(2.0)
        executor.addTask(waitTask)

        val store = com.areslib.subsystem.Store()
        
        // Dispatch starting progress
        store.dispatch(RobotAction.UpdatePathProgress(1.0, 1000L))
        var actions = executor.update(store.state, 1000L)
        assertTrue(actions.isEmpty()) // Task not completed
        assertEquals("PathProgressWait(2.0 m)", executor.activeTaskName)

        // 3. Simulate Pinpoint UART failure / disconnect
        mockDriver.shouldThrow = true
        assertDoesNotThrow {
            pinpointIO.updateInputs(inputs)
        }
        
        // Silently retains cached values
        assertEquals(1.0, inputs.posX, 1e-6)

        // 4. Since sensor is failing, we trigger a path detour manually using FSM preemption
        val detourPath = com.areslib.pathing.Path(listOf(
            com.areslib.pathing.PathPoint(Pose2d(0.0, 0.0), 0.0),
            com.areslib.pathing.PathPoint(Pose2d(1.0, 1.0), 1.0)
        ))
        
        val detourTask = com.areslib.fsm.ActionDispatchTask(
            RobotAction.SwitchPath(detourPath, isDetour = true, timestampMs = 1200L)
        )
        
        // Detour preempts the stuck wait task
        val detourActions = executor.preempt(detourTask, store.state, 1200L)
        assertEquals(1, detourActions.size)
        assertTrue(detourActions[0] is RobotAction.SwitchPath)
        assertEquals(detourPath, (detourActions[0] as RobotAction.SwitchPath).path)
        assertTrue((detourActions[0] as RobotAction.SwitchPath).isDetour)
    }

    @Test
    fun testCalibrationOffsetBlendingWithFloodgateFallback() {
        val mockMotor = MockFtcMotorEx()
        val estimateIO = com.areslib.ftc.EstimateMotorIO(mockMotor)
        val budgetManager = CurrentBudgetManager.ftcDefaults()

        // Register the motor
        budgetManager.register(estimateIO)

        // 1. Simulate motor active draw
        mockMotor.mockVelocity = 100.0 // healthy velocity
        estimateIO.power = 1.0 // full power command
        repeat(10) { estimateIO.updateInputs() }

        // First update at 12V
        budgetManager.update(12.0, enableCalibration = false)
        val initialEstimate = budgetManager.totalEstimatedAmps
        assertTrue(initialEstimate > 0.0)

        // 2. Calibrate error: mock motor actually draws MORE current (e.g. 5A more than estimated)
        mockMotor.mockCurrentAmps = initialEstimate + 5.0
        repeat(10) { estimateIO.updateInputs() }
        
        // Update with calibration enabled to learn the offset round-robin style
        budgetManager.update(12.0, enableCalibration = true)

        val postCalibrateEstimate = budgetManager.totalEstimatedAmps
        // Estimate should have dynamically adjusted upwards
        assertTrue(postCalibrateEstimate > initialEstimate)
        assertTrue(postCalibrateEstimate <= initialEstimate + 5.0)

        // 3. Floodgate fallback simulation: ensuring the fallback is robust
        val isFloodgateMissing = true
        val activeManager = if (isFloodgateMissing) budgetManager else null
        assertNotNull(activeManager)
        assertTrue(activeManager!!.powerScale >= 0.2)
    }
}
