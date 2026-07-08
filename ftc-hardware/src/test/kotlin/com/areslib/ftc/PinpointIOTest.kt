package com.areslib.ftc

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit

class PinpointIOTest {

    @Test
    fun testInitializeAndOffsetHandling() {
        val rawDriver = GoBildaPinpointDriver()
        val pinpointIO = PinpointIO(rawDriver)

        // 1. Initialize with an offset pose (e.g. Red alliance start: facing PI)
        val initialPose = Pose2d(x = 1.0, y = -1.0, heading = Rotation2d(Math.PI))
        pinpointIO.initialize(initialPose)
        Thread.sleep(50) // Wait for async initialization

        // Immediately after initialize (before raw movement), it should return the offset pose
        val initialUpdate = pinpointIO.getPoseUpdate()
        assertEquals(1.0, initialUpdate.xMeters, 1e-6)
        assertEquals(-1.0, initialUpdate.yMeters, 1e-6)
        assertEquals(com.areslib.math.InputMath.wrapAngle(Math.PI), initialUpdate.headingRadians, 1e-6)

        // 2. Simulate raw movement relative to the initial reset state
        // The raw driver heading is CW-positive (matching real GoBilda hardware).
        // PinpointIO negates it internally to produce CCW-positive output.
        // rawDriver.heading = 0.0 means no CW rotation from start → PinpointIO reads -0.0 = 0.0
        rawDriver.posX = 0.5 // moved 0.5m forward in driver frame
        rawDriver.posY = 0.0
        rawDriver.heading = 0.0  // No rotation (CW-positive raw)
        Thread.sleep(20) // Allow background thread to run

        val update1 = pinpointIO.getPoseUpdate()
        // offsetHeading = wrapAngle(PI - (-0.0)) = PI
        // rawHeading (negated) = -0.0 = 0.0
        // heading = wrapAngle(0.0 + PI) = PI
        // x_field = 0.5 * cos(PI) - 0.0 * sin(PI) + 1.0 = -0.5 + 1.0 = 0.5
        assertEquals(0.5, update1.xMeters, 1e-6)
        assertEquals(-1.0, update1.yMeters, 1e-6)
        assertEquals(com.areslib.math.InputMath.wrapAngle(Math.PI), update1.headingRadians, 1e-6)

        // 3. Simulate CW rotation (positive in raw hardware) and translation in driver frame
        // rawDriver.heading = 0.5 means 0.5 rad CW rotation in hardware
        // PinpointIO negates to -0.5 rad (CCW convention)
        rawDriver.posX = 1.0
        rawDriver.posY = 0.5
        rawDriver.heading = 0.5  // 0.5 rad CW in hardware → -0.5 rad CCW after negation
        Thread.sleep(20) // Allow background thread to run

        val update2 = pinpointIO.getPoseUpdate()
        // negatedRawHeading = -0.5
        // heading = wrapAngle(-0.5 + PI) = PI - 0.5
        // x_field = 1.0 * cos(PI) - 0.5 * sin(PI) + 1.0 = -1.0 + 1.0 = 0.0
        // y_field = 1.0 * sin(PI) + 0.5 * cos(PI) - 1.0 = 0.0 - 0.5 - 1.0 = -1.5
        assertEquals(0.0, update2.xMeters, 1e-6)
        assertEquals(-1.5, update2.yMeters, 1e-6)
        assertEquals(com.areslib.math.InputMath.wrapAngle(-0.5 + Math.PI), update2.headingRadians, 1e-6)
    }

    @Test
    fun testSoftwareOnlyInitializeWithExistingRawOffsets() {
        val rawDriver = GoBildaPinpointDriver()
        val pinpointIO = PinpointIO(rawDriver)

        // Simulate some raw movement BEFORE initialization (e.g., robot moved before vision snap)
        // These are CW-positive raw hardware values
        rawDriver.posX = 2.0
        rawDriver.posY = 1.0
        rawDriver.heading = 0.5  // 0.5 rad CW in hardware

        // Initialize with a snap pose (e.g. at (3.0, 4.0, 1.5)) without resetting hardware
        val snapPose = Pose2d(x = 3.0, y = 4.0, heading = Rotation2d(1.5))
        pinpointIO.initialize(snapPose, resetHardware = false)
        Thread.sleep(50) // Wait for async initialization

        // Immediately after initialize (before raw movement changes), it should return the snapPose
        val snapUpdate = pinpointIO.getPoseUpdate()
        assertEquals(3.0, snapUpdate.xMeters, 1e-6)
        assertEquals(4.0, snapUpdate.yMeters, 1e-6)
        assertEquals(1.5, snapUpdate.headingRadians, 1e-6)

        // If the robot now rotates further by +0.1 rad CW (hardware positive) and moves +0.5m along raw X:
        // CW hardware: heading goes from 0.5 to 0.6
        // PinpointIO negates: from -0.5 to -0.6, a change of -0.1 rad (CCW convention: CW rotation is negative)
        // So the CCW-positive heading should DECREASE by 0.1: from 1.5 to 1.4
        rawDriver.posX += 0.5
        rawDriver.heading += 0.1  // 0.1 rad further CW in hardware
        Thread.sleep(20) // Allow background thread to run

        val finalUpdate = pinpointIO.getPoseUpdate()
        // CW rotation in hardware → heading decreases in CCW convention
        assertEquals(1.4, finalUpdate.headingRadians, 1e-6)
    }
}
