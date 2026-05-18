package com.areslib.kinematics

import com.areslib.math.ChassisSpeeds
import com.areslib.math.Translation2d
import com.areslib.math.Rotation2d
import kotlin.math.hypot
import kotlin.math.atan2

class SwerveKinematics(
    val moduleTranslations: List<Translation2d>,
    val maxSteerVelRadPerSec: Double = Math.PI * 4.0,
    val maxSteerAccelRadPerSec2: Double = Math.PI * 8.0,
    val maxDriveAccelMps2: Double = 8.0
) {
    private val numModules = moduleTranslations.size
    private val previousSteerVels = DoubleArray(numModules) { 0.0 }
    private var previousStates: Array<SwerveModuleState>? = null

    constructor(vararg moduleTranslations: Translation2d) : this(moduleTranslations.toList())

    fun toSwerveModuleStates(chassisSpeeds: ChassisSpeeds, dtSeconds: Double = 0.02): Array<SwerveModuleState> {
        if (chassisSpeeds.vxMetersPerSecond == 0.0 && 
            chassisSpeeds.vyMetersPerSecond == 0.0 && 
            chassisSpeeds.omegaRadiansPerSecond == 0.0) {
            val stopped = Array(numModules) { SwerveModuleState() }
            previousStates = stopped
            return stopped
        }

        val targetStates = Array(numModules) { i ->
            val module = moduleTranslations[i]
            val vx = chassisSpeeds.vxMetersPerSecond - chassisSpeeds.omegaRadiansPerSecond * module.y
            val vy = chassisSpeeds.vyMetersPerSecond + chassisSpeeds.omegaRadiansPerSecond * module.x
            
            val speed = hypot(vx, vy)
            val angle = atan2(vy, vx)
            
            SwerveModuleState(speed, Rotation2d(angle))
        }

        val prev = previousStates
        val limitedStates = if (prev != null && dtSeconds > 1e-6) {
            Array(numModules) { i ->
                val pState = prev[i]
                val tState = targetStates[i]

                // Limit drive wheel acceleration
                val maxSpeedChange = maxDriveAccelMps2 * dtSeconds
                val speedChange = tState.speedMetersPerSecond - pState.speedMetersPerSecond
                val limitedSpeed = pState.speedMetersPerSecond + 
                    speedChange.coerceIn(-maxSpeedChange, maxSpeedChange)

                // Limit steering velocity and acceleration
                val targetAngleRad = tState.angle.radians
                val prevAngleRad = pState.angle.radians

                // Compute shortest angle difference
                var angleDiff = targetAngleRad - prevAngleRad
                while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI
                while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI

                // Target steering velocity
                val targetSteerVel = angleDiff / dtSeconds
                // Clamp steering velocity
                val limitedSteerVel = targetSteerVel.coerceIn(-maxSteerVelRadPerSec, maxSteerVelRadPerSec)

                // Clamp steering acceleration
                val prevSteerVel = previousSteerVels[i]
                val steerVelChange = limitedSteerVel - prevSteerVel
                val maxSteerVelChange = maxSteerAccelRadPerSec2 * dtSeconds
                val finalSteerVel = prevSteerVel + steerVelChange.coerceIn(-maxSteerVelChange, maxSteerVelChange)

                previousSteerVels[i] = finalSteerVel

                val finalAngle = prevAngleRad + finalSteerVel * dtSeconds
                SwerveModuleState(limitedSpeed, Rotation2d(finalAngle))
            }
        } else {
            targetStates
        }

        previousStates = limitedStates
        return limitedStates
    }
}

