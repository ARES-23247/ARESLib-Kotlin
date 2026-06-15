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

        // Immediately after initialize (before raw movement), it should return the offset pose
        val initialUpdate = pinpointIO.getPoseUpdate()
        assertEquals(1.0, initialUpdate.xMeters, 1e-6)
        assertEquals(-1.0, initialUpdate.yMeters, 1e-6)
        assertEquals(com.areslib.math.InputMath.wrapAngle(Math.PI), initialUpdate.headingRadians, 1e-6)

        // 2. Simulate raw movement relative to the initial reset state
        // Say the robot drives "forward" relative to the pinpoint sensor (positive raw X direction)
        // Since it is physically oriented facing PI (towards negative X on the field),
        // driving raw positive X should translate to negative X on the field.
        rawDriver.posX = 0.5 // moved 0.5m forward in driver frame
        rawDriver.posY = 0.0
        rawDriver.heading = 0.0
        Thread.sleep(20) // Allow background thread to run

        val update1 = pinpointIO.getPoseUpdate()
        // x_field = rawX * cos(PI) - rawY * sin(PI) + offset_x = 0.5 * (-1) - 0 + 1.0 = 0.5
        assertEquals(0.5, update1.xMeters, 1e-6)
        assertEquals(-1.0, update1.yMeters, 1e-6)
        assertEquals(com.areslib.math.InputMath.wrapAngle(Math.PI), update1.headingRadians, 1e-6)

        // 3. Simulate rotation and translation in driver frame
        rawDriver.posX = 1.0
        rawDriver.posY = 0.5
        rawDriver.heading = 0.5 // rotated 0.5 rad
        Thread.sleep(20) // Allow background thread to run

        val update2 = pinpointIO.getPoseUpdate()
        // x_field = 1.0 * cos(PI) - 0.5 * sin(PI) + 1.0 = -1.0 + 1.0 = 0.0
        // y_field = 1.0 * sin(PI) + 0.5 * cos(PI) - 1.0 = 0.0 - 0.5 - 1.0 = -1.5
        // heading_field = wrapped(0.5 + PI)
        assertEquals(0.0, update2.xMeters, 1e-6)
        assertEquals(-1.5, update2.yMeters, 1e-6)
        assertEquals(com.areslib.math.InputMath.wrapAngle(0.5 + Math.PI), update2.headingRadians, 1e-6)
    }

    @Test
    fun testSoftwareOnlyInitializeWithExistingRawOffsets() {
        val rawDriver = GoBildaPinpointDriver()
        val pinpointIO = PinpointIO(rawDriver)

        // Simulate some raw movement BEFORE initialization (e.g., robot moved before vision snap)
        rawDriver.posX = 2.0
        rawDriver.posY = 1.0
        rawDriver.heading = 0.5

        // Initialize with a snap pose (e.g. at (3.0, 4.0, 1.5)) without resetting hardware
        val snapPose = Pose2d(x = 3.0, y = 4.0, heading = Rotation2d(1.5))
        pinpointIO.initialize(snapPose, resetHardware = false)

        // Immediately after initialize (before raw movement changes), it should return the snapPose
        val snapUpdate = pinpointIO.getPoseUpdate()
        assertEquals(3.0, snapUpdate.xMeters, 1e-6)
        assertEquals(4.0, snapUpdate.yMeters, 1e-6)
        assertEquals(1.5, snapUpdate.headingRadians, 1e-6)

        // If the robot now rotates further by +0.1 rad and moves +0.5m along raw X:
        rawDriver.posX += 0.5
        rawDriver.heading += 0.1
        Thread.sleep(20) // Allow background thread to run

        val finalUpdate = pinpointIO.getPoseUpdate()
        // The heading should change to 1.6
        assertEquals(1.6, finalUpdate.headingRadians, 1e-6)
    }
}
