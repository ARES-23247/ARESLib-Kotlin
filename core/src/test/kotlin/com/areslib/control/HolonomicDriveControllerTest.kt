package com.areslib.control

import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.pathing.PathPoint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HolonomicDriveControllerTest {

    @Test
    fun `calculate outputs zero speeds when at target`() {
        val xController = PIDController(1.0, 0.0, 0.0)
        val yController = PIDController(1.0, 0.0, 0.0)
        val thetaController = PIDController(1.0, 0.0, 0.0)
        val controller = HolonomicDriveController(xController, yController, thetaController)
        
        val targetPose = Pose2d(1.0, 1.0, Rotation2d.fromDegrees(0.0))
        
        val output = controller.calculate(
            currentPose = Pose2d(1.0, 1.0, Rotation2d.fromDegrees(0.0)),
            targetPose = targetPose,
            targetVelocityMps = 0.0,
            targetHeading = Rotation2d.fromDegrees(0.0),
            dtSeconds = 0.02
        )
        
        assertEquals(0.0, output.vxMetersPerSecond, 0.001)
        assertEquals(0.0, output.vyMetersPerSecond, 0.001)
        assertEquals(0.0, output.omegaRadiansPerSecond, 0.001)
    }
    
    @Test
    fun `calculate outputs positive x when behind target`() {
        val xController = PIDController(1.0, 0.0, 0.0)
        val yController = PIDController(1.0, 0.0, 0.0)
        val thetaController = PIDController(1.0, 0.0, 0.0)
        val controller = HolonomicDriveController(xController, yController, thetaController)
        
        val targetPose = Pose2d(1.0, 0.0, Rotation2d.fromDegrees(0.0))
        
        val output = controller.calculate(
            currentPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0)),
            targetPose = targetPose,
            targetVelocityMps = 0.0,
            targetHeading = Rotation2d.fromDegrees(0.0),
            dtSeconds = 0.02
        )
        
        assertEquals(1.0, output.vxMetersPerSecond, 0.001)
        assertEquals(0.0, output.vyMetersPerSecond, 0.001)
        assertEquals(0.0, output.omegaRadiansPerSecond, 0.001)
    }
}
