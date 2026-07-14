package com.areslib.kinematics

import com.areslib.math.geometry.ChassisSpeeds
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MecanumKinematicsTest {

    private val kinematics = MecanumKinematics(trackWidthMeters = 0.5, wheelBaseMeters = 0.5)

    @Test
    fun `forward ChassisSpeeds gives equal positive wheel speeds`() {
        val speeds = ChassisSpeeds(vxMetersPerSecond = 1.0, vyMetersPerSecond = 0.0, omegaRadiansPerSecond = 0.0)
        val wheels = kinematics.toWheelSpeeds(speeds)
        
        assertEquals(1.0, wheels.frontLeftMetersPerSecond, 0.001)
        assertEquals(1.0, wheels.frontRightMetersPerSecond, 0.001)
        assertEquals(1.0, wheels.backLeftMetersPerSecond, 0.001)
        assertEquals(1.0, wheels.backRightMetersPerSecond, 0.001)
    }

    @Test
    fun `strafe left ChassisSpeeds gives alternating wheel speeds`() {
        val speeds = ChassisSpeeds(vxMetersPerSecond = 0.0, vyMetersPerSecond = 1.0, omegaRadiansPerSecond = 0.0)
        val wheels = kinematics.toWheelSpeeds(speeds)
        
        assertEquals(-1.0, wheels.frontLeftMetersPerSecond, 0.001)
        assertEquals(1.0, wheels.frontRightMetersPerSecond, 0.001)
        assertEquals(1.0, wheels.backLeftMetersPerSecond, 0.001)
        assertEquals(-1.0, wheels.backRightMetersPerSecond, 0.001)
    }
    
    @Test
    fun `normalize limits wheel speeds properly`() {
        val wheels = MecanumWheelSpeeds(2.0, 1.0, -2.0, 0.5)
        val normalized = wheels.normalize(1.0)
        
        assertEquals(1.0, normalized.frontLeftMetersPerSecond, 0.001)
        assertEquals(0.5, normalized.frontRightMetersPerSecond, 0.001)
        assertEquals(-1.0, normalized.backLeftMetersPerSecond, 0.001)
        assertEquals(0.25, normalized.backRightMetersPerSecond, 0.001)
    }
}

