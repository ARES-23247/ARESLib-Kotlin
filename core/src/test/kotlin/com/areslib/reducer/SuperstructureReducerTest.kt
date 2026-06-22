package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.SuperstructureMode
import com.areslib.state.SuperstructureState
import com.areslib.util.RobotClock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Championship-grade test suite for [SuperstructureReducer].
 *
 * Validates every FSM transition in the superstructure state machine including
 * intake toggling, flywheel spinup/ready transitions, shooting interlocks,
 * and autonomous path event triggers.
 */
class SuperstructureReducerTest {

    @BeforeEach
    fun setUp() {
        RobotClock.useMockTime(1000L)
        // Ensure the default strategy is active (tests are isolated)
        SuperstructureReducer.strategy = DefaultSuperstructureTransition
    }

    @AfterEach
    fun tearDown() {
        RobotClock.useSystemTime()
    }

    private fun now(): Long = RobotClock.currentTimeMillis()

    // ─── Idle <-> Intaking ──────────────────────────────────────────────

    @Test
    fun `idle to intaking when SetIntakeActive true`() {
        val state = SuperstructureState()
        assertEquals(SuperstructureMode.IDLE, state.mode)

        val next = SuperstructureReducer.reduce(state, RobotAction.SetIntakeActive(true, now()))
        assertEquals(SuperstructureMode.INTAKING, next.mode)
        assertTrue(next.intakeActive)
    }

    @Test
    fun `intaking to idle when SetIntakeActive false`() {
        val state = SuperstructureState(mode = SuperstructureMode.INTAKING, intakeActive = true)

        val next = SuperstructureReducer.reduce(state, RobotAction.SetIntakeActive(false, now()))
        assertEquals(SuperstructureMode.IDLE, next.mode)
        assertFalse(next.intakeActive)
    }

    @Test
    fun `SetIntakeActive true is idempotent`() {
        val state = SuperstructureState(mode = SuperstructureMode.INTAKING, intakeActive = true)

        val next = SuperstructureReducer.reduce(state, RobotAction.SetIntakeActive(true, now()))
        assertEquals(SuperstructureMode.INTAKING, next.mode)
        assertTrue(next.intakeActive)
    }

    // ─── Idle -> Flywheel Spinup ────────────────────────────────────────

    @Test
    fun `idle to flywheel spinup when SetFlywheelActive true`() {
        val state = SuperstructureState()

        val next = SuperstructureReducer.reduce(state, RobotAction.SetFlywheelActive(true, now()))
        assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, next.mode)
        assertTrue(next.flywheelActive)
    }

    @Test
    fun `flywheel off returns to idle`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_SPINUP,
            flywheelActive = true,
            flywheelRPM = 2000.0
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetFlywheelActive(false, now()))
        assertEquals(SuperstructureMode.IDLE, next.mode)
        assertFalse(next.flywheelActive)
    }

    // ─── Spinup -> Ready ────────────────────────────────────────────────

    @Test
    fun `spinup stays spinup when RPM below 95 pct of target`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_SPINUP,
            flywheelActive = true,
            flywheelRPM = 3000.0,
            flywheelTargetRPM = 4000.0
        )

        // 3900 RPM = 97.5% but let's use a lower value first: 3700 = 92.5%
        val next = SuperstructureReducer.reduce(state, RobotAction.UpdateFlywheelRPM(3700.0, now()))
        assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, next.mode,
            "3700 RPM (92.5% of 4000) should stay SPINUP")
    }

    @Test
    fun `spinup transitions to ready when RPM reaches 95 pct of target`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_SPINUP,
            flywheelActive = true,
            flywheelRPM = 3000.0,
            flywheelTargetRPM = 4000.0
        )

        // 3850 RPM = 96.25% of 4000 > 95% threshold
        val next = SuperstructureReducer.reduce(state, RobotAction.UpdateFlywheelRPM(3850.0, now()))
        assertEquals(SuperstructureMode.FLYWHEEL_READY, next.mode,
            "3850 RPM (96.25% of 4000) should transition to FLYWHEEL_READY")
        assertEquals(3850.0, next.flywheelRPM, 1e-6)
    }

    @Test
    fun `exact 95 pct boundary transitions to ready`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_SPINUP,
            flywheelActive = true,
            flywheelRPM = 0.0,
            flywheelTargetRPM = 4000.0
        )

        // Exactly 95%: 3800 RPM
        val next = SuperstructureReducer.reduce(state, RobotAction.UpdateFlywheelRPM(3800.0, now()))
        assertEquals(SuperstructureMode.FLYWHEEL_READY, next.mode,
            "Exactly 3800 RPM (95% of 4000) should be FLYWHEEL_READY")
    }

    @Test
    fun `just below 95 pct stays in spinup`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_SPINUP,
            flywheelActive = true,
            flywheelRPM = 0.0,
            flywheelTargetRPM = 4000.0
        )

        // 3799.99 RPM < 95%
        val next = SuperstructureReducer.reduce(state, RobotAction.UpdateFlywheelRPM(3799.99, now()))
        assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, next.mode,
            "3799.99 RPM (just below 95% of 4000) should stay FLYWHEEL_SPINUP")
    }

    // ─── Ready -> Shooting ──────────────────────────────────────────────

    @Test
    fun `ready to shooting when flywheel at speed and inventory greater than 0`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_READY,
            flywheelActive = true,
            flywheelRPM = 3900.0,
            flywheelTargetRPM = 4000.0,
            inventoryCount = 2
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetTransferActive(true, now()))
        assertEquals(SuperstructureMode.SHOOTING, next.mode)
        assertTrue(next.transferActive)
    }

    // ─── Cannot Shoot Without Inventory ─────────────────────────────────

    @Test
    fun `cannot shoot with zero inventory even when flywheel ready`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_READY,
            flywheelActive = true,
            flywheelRPM = 3900.0,
            flywheelTargetRPM = 4000.0,
            inventoryCount = 0
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetTransferActive(true, now()))
        assertNotEquals(SuperstructureMode.SHOOTING, next.mode,
            "Should not enter SHOOTING mode with 0 inventory")
        assertFalse(next.transferActive,
            "Transfer should not activate with 0 inventory")
    }

    // ─── Cannot Shoot Without Flywheel ──────────────────────────────────

    @Test
    fun `cannot shoot when flywheel not active even with inventory`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.IDLE,
            flywheelActive = false,
            flywheelRPM = 0.0,
            inventoryCount = 3
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetTransferActive(true, now()))
        assertNotEquals(SuperstructureMode.SHOOTING, next.mode,
            "Should not shoot without flywheel active")
        assertFalse(next.transferActive)
    }

    @Test
    fun `cannot shoot when flywheel active but not at speed`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_SPINUP,
            flywheelActive = true,
            flywheelRPM = 2000.0,
            flywheelTargetRPM = 4000.0,
            inventoryCount = 2
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetTransferActive(true, now()))
        assertNotEquals(SuperstructureMode.SHOOTING, next.mode,
            "Should not shoot when flywheel not at speed")
        assertFalse(next.transferActive)
    }

    // ─── Flywheel Off Stops Transfer ────────────────────────────────────

    @Test
    fun `flywheel off during shooting stops transfer`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.SHOOTING,
            flywheelActive = true,
            transferActive = true,
            flywheelRPM = 3900.0,
            flywheelTargetRPM = 4000.0,
            inventoryCount = 1
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetFlywheelActive(false, now()))
        assertFalse(next.transferActive, "Transfer should deactivate when flywheel turns off")
        assertFalse(next.flywheelActive)
        assertEquals(SuperstructureMode.IDLE, next.mode)
    }

    @Test
    fun `flywheel off during shooting with intake active returns to intaking`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.SHOOTING,
            intakeActive = true,
            flywheelActive = true,
            transferActive = true,
            flywheelRPM = 3900.0,
            flywheelTargetRPM = 4000.0,
            inventoryCount = 1
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetFlywheelActive(false, now()))
        assertFalse(next.transferActive)
        assertEquals(SuperstructureMode.INTAKING, next.mode,
            "Should return to INTAKING when flywheel off but intake still active")
    }

    // ─── SetFlywheelTargetRPM Mode Recalculation ────────────────────────

    @Test
    fun `changing target RPM recalculates mode from ready to spinup`() {
        // Flywheel at 3900 RPM, target was 4000 (at speed). Now raise target to 5000.
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_READY,
            flywheelActive = true,
            flywheelRPM = 3900.0,
            flywheelTargetRPM = 4000.0
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetFlywheelTargetRPM(5000.0, now()))
        assertEquals(5000.0, next.flywheelTargetRPM, 1e-6)
        // 3900/5000 = 78% < 95%, so should drop to SPINUP
        assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, next.mode,
            "Raising target RPM should recalculate to SPINUP if RPM now below 95% threshold")
    }

    @Test
    fun `lowering target RPM can promote spinup to ready`() {
        // Flywheel at 3900 RPM, target was 5000 (not at speed). Lower target to 4000.
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_SPINUP,
            flywheelActive = true,
            flywheelRPM = 3900.0,
            flywheelTargetRPM = 5000.0
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetFlywheelTargetRPM(4000.0, now()))
        assertEquals(4000.0, next.flywheelTargetRPM, 1e-6)
        // 3900/4000 = 97.5% > 95%, should promote to READY
        assertEquals(SuperstructureMode.FLYWHEEL_READY, next.mode,
            "Lowering target RPM should promote to READY when RPM exceeds 95% of new target")
    }

    @Test
    fun `SetFlywheelTargetRPM with flywheel off stays idle`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.IDLE,
            flywheelActive = false
        )

        val next = SuperstructureReducer.reduce(state, RobotAction.SetFlywheelTargetRPM(3000.0, now()))
        assertEquals(SuperstructureMode.IDLE, next.mode)
        assertEquals(3000.0, next.flywheelTargetRPM, 1e-6)
    }

    // ─── PathEventTriggered IntakeOn/Off ─────────────────────────────────

    @Test
    fun `PathEventTriggered IntakeOn activates intake`() {
        val state = SuperstructureState()

        val next = SuperstructureReducer.reduce(
            state,
            RobotAction.PathEventTriggered("IntakeOn", now())
        )
        assertTrue(next.intakeActive)
        assertEquals(SuperstructureMode.INTAKING, next.mode)
    }

    @Test
    fun `PathEventTriggered IntakeOff deactivates intake`() {
        val state = SuperstructureState(mode = SuperstructureMode.INTAKING, intakeActive = true)

        val next = SuperstructureReducer.reduce(
            state,
            RobotAction.PathEventTriggered("IntakeOff", now())
        )
        assertFalse(next.intakeActive)
        assertEquals(SuperstructureMode.IDLE, next.mode)
    }

    @Test
    fun `PathEventTriggered IntakeOff with flywheel active returns to flywheel mode`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.INTAKING,
            intakeActive = true,
            flywheelActive = true,
            flywheelRPM = 3900.0,
            flywheelTargetRPM = 4000.0
        )

        val next = SuperstructureReducer.reduce(
            state,
            RobotAction.PathEventTriggered("IntakeOff", now())
        )
        assertFalse(next.intakeActive)
        assertEquals(SuperstructureMode.FLYWHEEL_READY, next.mode,
            "IntakeOff with flywheel at speed should return to FLYWHEEL_READY")
    }

    @Test
    fun `PathEventTriggered IntakeOff with flywheel spinning up returns to spinup`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.INTAKING,
            intakeActive = true,
            flywheelActive = true,
            flywheelRPM = 2000.0,
            flywheelTargetRPM = 4000.0
        )

        val next = SuperstructureReducer.reduce(
            state,
            RobotAction.PathEventTriggered("IntakeOff", now())
        )
        assertFalse(next.intakeActive)
        assertEquals(SuperstructureMode.FLYWHEEL_SPINUP, next.mode,
            "IntakeOff with flywheel not at speed should return to FLYWHEEL_SPINUP")
    }

    @Test
    fun `PathEventTriggered unknown event does not change state`() {
        val state = SuperstructureState(mode = SuperstructureMode.IDLE, intakeActive = false)

        val next = SuperstructureReducer.reduce(
            state,
            RobotAction.PathEventTriggered("SomeOtherEvent", now())
        )
        assertEquals(state, next, "Unknown path event should not modify state")
    }

    // ─── SetInventoryCount ──────────────────────────────────────────────

    @Test
    fun `SetInventoryCount updates inventory`() {
        val state = SuperstructureState(inventoryCount = 0)

        val next = SuperstructureReducer.reduce(state, RobotAction.SetInventoryCount(3, now()))
        assertEquals(3, next.inventoryCount)
    }

    // ─── Unrelated Action ───────────────────────────────────────────────

    @Test
    fun `unrelated action does not mutate superstructure state`() {
        val state = SuperstructureState(
            mode = SuperstructureMode.FLYWHEEL_READY,
            flywheelActive = true,
            flywheelRPM = 3900.0,
            inventoryCount = 2
        )

        // JoystickDriveIntent is not handled by SuperstructureReducer
        val next = SuperstructureReducer.reduce(
            state,
            RobotAction.JoystickDriveIntent(0.5, 0.5, 0.0, now())
        )
        assertEquals(state, next, "Unrelated actions should pass through state unchanged")
    }
}
