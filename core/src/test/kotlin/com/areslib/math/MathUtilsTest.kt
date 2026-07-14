package com.areslib.math

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MathUtilsTest {

    @Test
    fun testWrapAngle() {
        // Zero cases
        assertEquals(0.0, wrapAngle(0.0), 1e-6)
        assertEquals(0.0, wrapAngle(Double.NaN), 1e-6)
        assertEquals(0.0, wrapAngle(Double.POSITIVE_INFINITY), 1e-6)

        // Simple wrapping cases
        assertEquals(-Math.PI, wrapAngle(Math.PI), 1e-6)
        assertEquals(-Math.PI, wrapAngle(-Math.PI), 1e-6)
        assertEquals(0.0, wrapAngle(2.0 * Math.PI), 1e-6)
        assertEquals(Math.PI / 2.0, wrapAngle(Math.PI / 2.0), 1e-6)
        assertEquals(-Math.PI / 2.0, wrapAngle(-Math.PI / 2.0), 1e-6)
        assertEquals(-Math.PI / 2.0, wrapAngle(1.5 * Math.PI), 1e-6)
        assertEquals(Math.PI / 2.0, wrapAngle(-1.5 * Math.PI), 1e-6)
    }
}
