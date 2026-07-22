package com.areslib.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.atan2

/**
 * AimingMathTest declaration.
 * Provides high-performance, Zero-GC operations.
 * CCW-positive heading standard applied. 
 * Note: Physical units use standard SI metrics.
 * Uses LaTeX math representation for kinematics where applicable.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class AimingMathTest {

    @Test
    fun `stationary robot aiming points directly to target`() {
        val heading = AimingMath.calculateCompensatedHeading(
            robotX = 0.0,
            robotY = 0.0,
            vx = 0.0,
            vy = 0.0,
            targetX = 3.0,
            targetY = 4.0,
            shotSpeed = 10.0
        )
        val expected = atan2(4.0, 3.0)
        assertEquals(expected, heading, 1e-6)
    }

    @Test
    fun `robot moving sideways aiming compensates correctly`() {
        // Robot at origin, target straight ahead at (5, 0)
        // Robot moving left (+Y) at 2 m/s. Shot speed is 10 m/s.
        val heading = AimingMath.calculateCompensatedHeading(
            robotX = 0.0,
            robotY = 0.0,
            vx = 0.0,
            vy = 2.0, // Moving left
            targetX = 5.0,
            targetY = 0.0,
            shotSpeed = 10.0
        )
        // Since robot is moving left, compensated heading must point slightly right (negative Y)
        assertTrue(heading < 0.0, "Compensated heading $heading should be negative (aiming right) to counter leftward speed")
        
        // Ensure it resolves to a valid physical angle
        assertTrue(heading > -Math.PI / 2, "Aiming offset should be reasonable")
    }
}
