package com.areslib.auto

import com.areslib.math.Translation2d
import com.areslib.math.Rotation2d
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HolonomicDriveControllerTest {

    @Test
    fun `calculate outputs zero speeds when at target`() {
        val controller = HolonomicDriveController(1.0, 1.0)
        
        val target = TrajectoryState(
            timeSeconds = 1.0,
            velocityMetersPerSecond = 0.0,
            poseMeters = Translation2d(1.0, 1.0),
            headingRadians = Rotation2d.fromDegrees(0.0)
        )
        
        val output = controller.calculate(
            currentPose = Translation2d(1.0, 1.0),
            currentHeading = Rotation2d.fromDegrees(0.0),
            targetState = target
        )
        
        assertEquals(0.0, output.vxMetersPerSecond, 0.001)
        assertEquals(0.0, output.vyMetersPerSecond, 0.001)
        assertEquals(0.0, output.omegaRadiansPerSecond, 0.001)
    }
    
    @Test
    fun `calculate outputs positive x when behind target`() {
        val controller = HolonomicDriveController(1.0, 1.0)
        
        val target = TrajectoryState(
            timeSeconds = 1.0,
            velocityMetersPerSecond = 0.0, // pure feedback response
            poseMeters = Translation2d(1.0, 0.0),
            headingRadians = Rotation2d.fromDegrees(0.0)
        )
        
        val output = controller.calculate(
            currentPose = Translation2d(0.0, 0.0),
            currentHeading = Rotation2d.fromDegrees(0.0),
            targetState = target
        )
        
        assertEquals(1.0, output.vxMetersPerSecond, 0.001)
        assertEquals(0.0, output.vyMetersPerSecond, 0.001)
    }
}
