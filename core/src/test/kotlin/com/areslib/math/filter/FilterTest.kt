package com.areslib.math.filter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterTest {

    @Test
    fun testLowPassFilterSmoothing() {
        // Time constant = 0.1 seconds
        val filter = LowPassFilter(0.1)

        // First calculation should snap directly to the measurement (no history yet)
        assertEquals(10.0, filter.calculate(10.0, 0.02), 1e-6)

        // Second calculation with dt = 0.02s
        // Alpha = dt / (RC + dt) = 0.02 / (0.1 + 0.02) = 0.02 / 0.12 = 1/6 = 0.166667
        // Expected value = alpha * 20.0 + (1 - alpha) * 10.0 = 10.0 + (1/6 * 10.0) = 11.666667
        val expected = (1.0 / 6.0) * 20.0 + (5.0 / 6.0) * 10.0
        assertEquals(expected, filter.calculate(20.0, 0.02), 1e-5)
    }

    @Test
    fun testLowPassFilterBypass() {
        // Zero time constant means no filtering (snap immediately)
        val filter = LowPassFilter(0.0)
        
        assertEquals(5.0, filter.calculate(5.0, 0.02), 1e-6)
        assertEquals(10.0, filter.calculate(10.0, 0.02), 1e-6)
    }

    @Test
    fun testLowPassFilterReset() {
        val filter = LowPassFilter(0.5)
        filter.calculate(10.0, 0.02)
        
        filter.reset(25.0)
        assertEquals(25.0, filter.value, 1e-6)
        
        // Next calculate should filter from 25.0
        val out = filter.calculate(30.0, 0.05)
        // alpha = 0.05 / (0.5 + 0.05) = 0.05 / 0.55 = 1/11
        val expected = (1.0 / 11.0) * 30.0 + (10.0 / 11.0) * 25.0
        assertEquals(expected, out, 1e-5)
    }

    @Test
    fun testLowPassFilterClear() {
        val filter = LowPassFilter(0.2)
        filter.calculate(10.0, 0.02)
        
        filter.clear()
        // Clearing should make the next input snap directly
        assertEquals(50.0, filter.calculate(50.0, 0.02), 1e-6)
    }

    @Test
    fun testLowPassFilterNonFiniteInputPreservesEstimate() {
        val filter = LowPassFilter(0.1)
        filter.calculate(10.0, 0.02)

        // NaN or Infinity should return previous valid estimate
        assertEquals(10.0, filter.calculate(Double.NaN, 0.02), 1e-6)
        assertEquals(10.0, filter.calculate(Double.POSITIVE_INFINITY, 0.02), 1e-6)
        assertEquals(10.0, filter.calculate(10.0, Double.NaN), 1e-6)
    }

    @Test
    fun testLowPassFilterNonPositiveDtPreservesEstimate() {
        val filter = LowPassFilter(0.1)
        filter.calculate(10.0, 0.02)

        // dt <= 0 should yield alpha = 0 -> lastEstimate preserved
        val out = filter.calculate(20.0, 0.0)
        assertEquals(10.0, out, 1e-6)
        assertEquals(10.0, filter.calculate(20.0, -0.01), 1e-6)
    }

    @Test
    fun testLowPassFilterSetTimeConstant() {
        val filter = LowPassFilter(0.1)
        filter.calculate(10.0, 0.02)

        // Change time constant to 0.0 -> bypass filter
        filter.setTimeConstant(0.0)
        assertEquals(20.0, filter.calculate(20.0, 0.02), 1e-6)
    }

    @Test
    fun testLowPassFilterNegativeTimeConstantBypassAndDefaultReset() {
        // 1. Negative time constant acts as passthrough bypass, updating lastEstimate directly
        val filter = LowPassFilter(-0.5)
        assertEquals(10.0, filter.calculate(10.0, 0.02), 1e-6)
        assertEquals(10.0, filter.value, 1e-6)

        assertEquals(25.0, filter.calculate(25.0, 0.02), 1e-6)
        assertEquals(25.0, filter.value, 1e-6)

        // 2. setTimeConstant(Double.NaN) safely preserves lastEstimate without numerical corruption
        filter.setTimeConstant(Double.NaN)
        val preserved = filter.calculate(50.0, 0.02)
        assertEquals(25.0, preserved, 1e-6)
        assertEquals(25.0, filter.value, 1e-6)

        // 3. Parameterless reset() seeds lastEstimate to 0.0 and flags hasFirstValue = true
        filter.reset()
        assertEquals(0.0, filter.value, 1e-6)

        // Verify hasFirstValue is true: subsequent calculate filters against 0.0 baseline instead of snapping
        filter.setTimeConstant(0.1)
        // alpha = 0.02 / (0.1 + 0.02) = 1/6
        // output = (1/6) * 12.0 + (5/6) * 0.0 = 2.0
        val filtered = filter.calculate(12.0, 0.02)
        assertEquals(2.0, filtered, 1e-5)
        assertEquals(2.0, filter.value, 1e-5)
    }
}
