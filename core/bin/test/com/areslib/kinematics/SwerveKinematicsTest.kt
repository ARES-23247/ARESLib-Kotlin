package com.areslib.kinematics

import com.areslib.math.ChassisSpeeds
import com.areslib.math.Translation2d
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SwerveKinematicsTest {

    @Test
    fun `test forward chassis speeds maps to identical module speeds`() {
        val kinematics = SwerveKinematics(
            Translation2d(0.5, 0.5),   // FL
            Translation2d(0.5, -0.5),  // FR
            Translation2d(-0.5, 0.5),  // BL
            Translation2d(-0.5, -0.5)  // BR
        )
        
        val speeds = ChassisSpeeds(vxMetersPerSecond = 2.0, vyMetersPerSecond = 0.0, omegaRadiansPerSecond = 0.0)
        
        val moduleStates = kinematics.toSwerveModuleStates(speeds)
        
        assertEquals(4, moduleStates.size)
        
        for (state in moduleStates) {
            assertEquals(2.0, state.speedMetersPerSecond, 0.001)
            assertEquals(0.0, state.angle.radians, 0.001)
        }
    }
}
