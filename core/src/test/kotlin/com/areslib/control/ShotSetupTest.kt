package com.areslib.control

import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.math.ChassisSpeeds
import com.areslib.math.Translation2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.*

class ShotSetupTest {

    @Test
    fun testStaticShot() {
        val robotPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        val speeds = ChassisSpeeds(0.0, 0.0, 0.0)
        val target = Translation2d(4.0, 0.0)
        
        val result = ShotResult()
        ShotSetup.calculate(robotPose, speeds, target, result)
        
        // Since the shooter is offset by -0.25m along X:
        // Compensated pos: X = 0.0, heading = 0.0
        // Shooter pos: X = -0.25, Y = 0.0
        // Target is at X = 4.0, Y = 0.0
        // Aim vector from shooter to target: dx = 4.25, dy = 0.0
        // Distance should be exactly 4.25
        assertEquals(4.25, result.aimDistanceMeters, 1e-6)
        
        // Aim angle is 0.0 (atan2(0, 4.25))
        assertEquals(0.0, result.aimAngleRad, 1e-6)
        
        // Robot target heading should be PI (180 degrees) to point the rear shooter at the target
        assertEquals(PI, abs(result.robotTargetHeadingRad), 1e-6)
        
        // Feedforward should be zero since speeds are zero
        assertEquals(0.0, result.angularVelocityFeedforwardRadPerSec, 1e-6)

        // Interpolation checks
        val expectedRpm = ShotSetup.interpolateRpm(4.25)
        assertEquals(expectedRpm, result.targetFlywheelRpm, 1e-6)
    }

    @Test
    fun testTranslatingShotCompensation() {
        val robotPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        // Robot translating at 2.0 m/s in +Y (field-centric)
        val speeds = ChassisSpeeds(0.0, 2.0, 0.0)
        val target = Translation2d(4.0, 0.0)
        
        val result = ShotResult()
        ShotSetup.calculate(robotPose, speeds, target, result)
        
        // Since robot is translating +Y, the shooter's velocity is +Y (2.0 m/s).
        // During the time of flight, the projectile gets carried along +Y.
        // Therefore, we must aim in the -Y direction (aimAngleRad < 0) to compensate.
        // Virtual target Y should be negative to aim behind the target.
        assertTrue(result.virtualTargetY < 0.0, "Virtual target Y should be negative for +Y robot velocity")
        assertTrue(result.aimAngleRad < 0.0, "Aim angle should be negative to lead the target")
    }

    @Test
    fun testRotatingShotCompensation() {
        val robotPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        // Robot rotating at 1.0 rad/s
        val speeds = ChassisSpeeds(0.0, 0.0, 1.0)
        val target = Translation2d(4.0, 0.0)
        
        val result = ShotResult()
        ShotSetup.calculate(robotPose, speeds, target, result)
        
        // Shooter is at (-0.25, 0). Under 1.0 rad/s counter-clockwise rotation,
        // it acquires a tangential velocity of omega * r = 1.0 * (-0.25) = -0.25 m/s along Y.
        // During the TOF, the projectile gets carried along -Y.
        // Therefore, we must aim in the +Y direction (aimAngleRad > 0) to compensate.
        assertTrue(result.virtualTargetY > 0.0, "Virtual target Y should be positive for negative shooter velocity")
        assertTrue(result.aimAngleRad > 0.0, "Aim angle should be positive to compensate")
    }
}
