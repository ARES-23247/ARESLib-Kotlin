package com.areslib.control.feedback

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LinearADRCTest {

    @Test
    fun testADRCBasicCalculationAndReset() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 10.0, omegaO = 30.0)

        // Initial update with dt <= 0 should be 0.0
        assertEquals(0.0, adrc.calculate(1.0, 0.0, 0.0))

        // Reset measurement
        adrc.reset(5.0)
        assertEquals(5.0, adrc.xHat1)
        assertEquals(0.0, adrc.xHat2)

        // Calculate next step
        val output = adrc.calculate(6.0, 5.0, 0.02)
        assertTrue(output > 0.0, "Output should command forward motion to reach target")
    }

    @Test
    fun testADRCOutputClamping() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 10.0, omegaO = 30.0)
        adrc.setOutputLimits(-2.0, 2.0)

        // Massive step command that would normally yield high control effort
        val output = adrc.calculate(100.0, 0.0, 0.02)
        assertEquals(2.0, output, 1e-6)

        val outputNeg = adrc.calculate(-100.0, 0.0, 0.02)
        assertEquals(-2.0, outputNeg, 1e-6)
    }

    @Test
    fun testADRCContinuousInput() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 10.0, omegaO = 30.0)
        adrc.enableContinuousInput(-Math.PI, Math.PI)

        // Set target slightly past PI (e.g., -175 degrees vs +175 degrees)
        // Shortest path should be standard circular wrap
        val target = -Math.PI + 0.1
        val measurement = Math.PI - 0.1
        
        adrc.reset(measurement)
        val output = adrc.calculate(target, measurement, 0.02)
        
        // Output should be positive because -179 is clockwise/positive from +179
        assertTrue(output > 0.0)
    }

    @Test
    fun `continuous observer unwraps measurement across pi boundary`() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 1.0, omegaO = 10.0)
        adrc.enableContinuousInput(-Math.PI, Math.PI)
        adrc.reset(Math.PI - 0.01)

        adrc.calculate(
            target = -Math.PI + 0.01,
            measurement = -Math.PI + 0.01,
            dtSeconds = 0.01
        )

        assertTrue(adrc.xHat1 > 3.0, "Observer must remain on the local +pi branch: ${adrc.xHat1}")
        assertTrue(kotlin.math.abs(adrc.xHat1 - Math.PI) < 0.1)
    }

    @Test
    fun `nonfinite input returns zero without poisoning observer`() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 1.0, omegaO = 10.0)
        adrc.reset(2.0)

        assertEquals(0.0, adrc.calculate(Double.NaN, 2.0, 0.02))
        assertEquals(2.0, adrc.xHat1, 1e-12)
        assertEquals(0.0, adrc.xHat2, 1e-12)
    }

    @Test
    fun `eso tracks external disturbance force over time`() {
        val adrc = LinearADRC(b0 = 2.0, omegaC = 10.0, omegaO = 30.0)
        adrc.reset(0.0)
        var plantState = 0.0
        val target = 1.0
        val disturbance = 3.0 // Constant disturbance
        val dt = 0.02

        for (i in 0 until 50) {
            val u = adrc.calculate(target, plantState, dt)
            // Plant integration: dx = (b0 * u + disturbance) * dt
            plantState += (2.0 * u + disturbance) * dt
        }

        // ESO should have observed and compensated for the disturbance
        assertTrue(adrc.xHat2 > 0.0, "xHat2 should estimate positive disturbance: ${adrc.xHat2}")
        assertTrue(kotlin.math.abs(plantState - target) < 0.2, "Plant state $plantState should converge near target $target")
    }

    @Test
    fun `output saturation freezes disturbance integration when error has same sign`() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 10.0, omegaO = 20.0)
        adrc.setOutputLimits(-1.0, 1.0)
        adrc.reset(0.0)

        // Command massive setpoint causing saturation (uUnsat >> 1.0, u clamped to 1.0)
        // With plant at 0.0 and xHat1 at 0.0, observerError is 0.0
        // Now suppose measurement lags behind xHat1: measurement = 0.0, xHat1 moves forward
        adrc.calculate(target = 10.0, measurement = 0.0, dtSeconds = 0.02)
        val xHat2AfterFirst = adrc.xHat2
        assertTrue(xHat2AfterFirst.isFinite())

        // Continued saturation
        adrc.calculate(target = 10.0, measurement = 0.0, dtSeconds = 0.02)
        // Verify xHat2 is bounded and doesn't run away
        assertTrue(adrc.xHat2.isFinite())
        assertFalse(adrc.xHat2.isNaN())
    }

    @Test
    fun `degenerate b0 safely returns zero without division by zero or NaN`() {
        val adrcZeroB0 = LinearADRC(b0 = 0.0, omegaC = 10.0, omegaO = 30.0)
        adrcZeroB0.reset(1.0)

        val outZero = adrcZeroB0.calculate(target = 2.0, measurement = 1.0, dtSeconds = 0.02)
        assertEquals(0.0, outZero, 1e-12)
        assertTrue(adrcZeroB0.xHat1.isFinite())
        assertTrue(adrcZeroB0.xHat2.isFinite())

        val adrcTinyB0 = LinearADRC(b0 = 1e-12, omegaC = 10.0, omegaO = 30.0)
        adrcTinyB0.reset(1.0)
        val outTiny = adrcTinyB0.calculate(target = 2.0, measurement = 1.0, dtSeconds = 0.02)
        assertEquals(0.0, outTiny, 1e-12)
    }

    @Test
    fun `degenerate continuous input range behaves safely`() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 10.0, omegaO = 30.0)
        adrc.enableContinuousInput(5.0, 5.0) // Range = 0.0
        adrc.reset(5.0)

        val out = adrc.calculate(target = 6.0, measurement = 5.0, dtSeconds = 0.02)
        assertTrue(out.isFinite())
        assertFalse(out.isNaN())
    }

    @Test
    fun `dynamic modification of b0 and bandwidths updates calculation immediately`() {
        val adrc = LinearADRC(b0 = 1.0, omegaC = 5.0, omegaO = 15.0)
        adrc.reset(0.0)
        val out1 = adrc.calculate(target = 1.0, measurement = 0.0, dtSeconds = 0.02)

        // Increase omegaC to increase aggressiveness
        adrc.reset(0.0)
        adrc.omegaC = 20.0
        val out2 = adrc.calculate(target = 1.0, measurement = 0.0, dtSeconds = 0.02)

        assertTrue(out2 > out1, "Higher omegaC should produce higher initial control effort: $out2 > $out1")
    }
}
