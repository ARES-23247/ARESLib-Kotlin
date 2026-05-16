package com.areslib.kinematics

import com.areslib.math.ChassisSpeeds
import com.areslib.math.Translation2d
import com.areslib.math.Rotation2d
import kotlin.math.hypot
import kotlin.math.atan2

class SwerveKinematics(vararg moduleTranslations: Translation2d) {
    private val modules = moduleTranslations.toList()
    private val numModules = modules.size

    fun toSwerveModuleStates(chassisSpeeds: ChassisSpeeds): Array<SwerveModuleState> {
        if (chassisSpeeds.vxMetersPerSecond == 0.0 && 
            chassisSpeeds.vyMetersPerSecond == 0.0 && 
            chassisSpeeds.omegaRadiansPerSecond == 0.0) {
            return Array(numModules) { SwerveModuleState() }
        }

        return Array(numModules) { i ->
            val module = modules[i]
            
            // v = v_c + omega x r
            // v_x = v_cx - omega * r_y
            // v_y = v_cy + omega * r_x
            
            val vx = chassisSpeeds.vxMetersPerSecond - chassisSpeeds.omegaRadiansPerSecond * module.y
            val vy = chassisSpeeds.vyMetersPerSecond + chassisSpeeds.omegaRadiansPerSecond * module.x
            
            val speed = hypot(vx, vy)
            val angle = atan2(vy, vx)
            
            SwerveModuleState(speed, Rotation2d(angle))
        }
    }
}
