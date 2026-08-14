package com.areslib.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AresGamepadDslTest {

    private lateinit var gamepad: AresGamepad
    private lateinit var state: GamepadState

    @BeforeEach
    fun setUp() {
        gamepad = AresGamepad()
        state = GamepadState()
    }

    @Test
    fun testButtonPressAndReleaseBindings() {
        var pressCount = 0
        var releaseCount = 0

        gamepad.a.bindTo("Test Action") {
            pressCount++
        }
        gamepad.a.onRelease("Release Action") {
            releaseCount++
        }

        // Initially not pressed
        state.a = false
        gamepad.update(state)
        assertEquals(0, pressCount)
        assertEquals(0, releaseCount)

        // Pressed
        state.a = true
        gamepad.update(state)
        assertEquals(1, pressCount)
        assertEquals(0, releaseCount)

        // Held (no new press)
        gamepad.update(state)
        assertEquals(1, pressCount)
        assertEquals(0, releaseCount)

        // Released
        state.a = false
        gamepad.update(state)
        assertEquals(1, pressCount)
        assertEquals(1, releaseCount)
    }

    @Test
    fun testButtonToggleBinding() {
        var activeState = false

        gamepad.x.toggle("Toggle Shooter", initial = false) { isEnabled ->
            activeState = isEnabled
        }

        state.x = true
        gamepad.update(state)
        assertTrue(activeState)

        state.x = false
        gamepad.update(state)
        assertTrue(activeState)

        state.x = true
        gamepad.update(state)
        assertFalse(activeState)
    }

    @Test
    fun testAxisDeadbandShaping() {
        gamepad.leftTrigger.withDeadband(0.10)

        // Within deadband -> zero
        state.leftTrigger = 0.05f
        gamepad.update(state)
        assertEquals(0.0, gamepad.leftTrigger.shapedValue, 1e-6)

        // Outside deadband -> linearly rescaled from 0 to 1
        state.leftTrigger = 0.55f
        gamepad.update(state)
        // (0.55 - 0.10) / (1.0 - 0.10) = 0.45 / 0.90 = 0.50
        assertEquals(0.50, gamepad.leftTrigger.shapedValue, 1e-4)

        // Full deflection -> 1.0
        state.leftTrigger = 1.0f
        gamepad.update(state)
        assertEquals(1.0, gamepad.leftTrigger.shapedValue, 1e-6)
    }

    @Test
    fun testAxisExponentialCurveShaping() {
        gamepad.rightStickX
            .withDeadband(0.0)
            .withExponentialCurve(2.0)

        state.rightStickX = 0.5f
        gamepad.update(state)
        assertEquals(0.25, gamepad.rightStickX.shapedValue, 1e-4)

        state.rightStickX = -0.5f
        gamepad.update(state)
        assertEquals(-0.25, gamepad.rightStickX.shapedValue, 1e-4)
    }

    @Test
    fun testStickRadialDeadband() {
        gamepad.leftStick.withDeadband(0.20)

        // Vector magnitude < 0.20 -> zeros both components
        state.leftStickX = 0.10f
        state.leftStickY = 0.10f
        // magnitude = sqrt(0.01 + 0.01) = ~0.1414 < 0.20
        gamepad.update(state)
        assertEquals(0.0, gamepad.leftStick.shapedX, 1e-6)
        assertEquals(0.0, gamepad.leftStick.shapedY, 1e-6)

        // Vector magnitude = 0.60
        state.leftStickX = 0.60f
        state.leftStickY = 0.0f
        gamepad.update(state)
        // scaled = (0.60 - 0.20) / (1.0 - 0.20) = 0.40 / 0.80 = 0.50
        assertEquals(0.50, gamepad.leftStick.shapedX, 1e-4)
        assertEquals(0.0, gamepad.leftStick.shapedY, 1e-4)
    }
}
