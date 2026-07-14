package com.areslib.kinematics

import com.areslib.math.geometry.ChassisSpeeds
import com.areslib.math.geometry.Translation2d
import com.areslib.math.geometry.Rotation2d
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
    private val stoppedStates = Array(numModules) { SwerveModuleState() }
    private val targetStatesBuffer = Array(numModules) { SwerveModuleState() }
    private val limitedStatesBuffer = Array(numModules) { SwerveModuleState() }

    constructor(vararg moduleTranslations: Translation2d) : this(moduleTranslations.toList())

    fun toSwerveModuleStates(chassisSpeeds: ChassisSpeeds, dtSeconds: Double = 0.02): Array<SwerveModuleState> {
        if (!dtSeconds.isFinite() || dtSeconds <= 0.0) {
            previousStates = stoppedStates
            return stoppedStates
        }

        if (!chassisSpeeds.vxMetersPerSecond.isFinite() ||
            !chassisSpeeds.vyMetersPerSecond.isFinite() ||
            !chassisSpeeds.omegaRadiansPerSecond.isFinite()) {
            previousStates = stoppedStates
            return stoppedStates
        }

        if (chassisSpeeds.vxMetersPerSecond == 0.0 && 
            chassisSpeeds.vyMetersPerSecond == 0.0 && 
            chassisSpeeds.omegaRadiansPerSecond == 0.0) {
            previousStates = stoppedStates
            return stoppedStates
        }

        for (i in 0 until numModules) {
            val module = moduleTranslations[i]
            val vx = chassisSpeeds.vxMetersPerSecond - chassisSpeeds.omegaRadiansPerSecond * module.y
            val vy = chassisSpeeds.vyMetersPerSecond + chassisSpeeds.omegaRadiansPerSecond * module.x
            
            val speed = hypot(vx, vy)
            val angle = atan2(vy, vx)
            
            targetStatesBuffer[i] = SwerveModuleState(speed, Rotation2d(angle))
        }

        val prev = previousStates
        val limitedStates = if (prev != null && dtSeconds > 1e-6) {
            for (i in 0 until numModules) {
                val pState = prev[i]
                val tState = targetStatesBuffer[i]

                // Limit drive wheel acceleration
                val rawMaxSpeedChange = maxDriveAccelMps2 * dtSeconds
                val maxSpeedChange = kotlin.math.abs(rawMaxSpeedChange)
                val speedChange = tState.speedMetersPerSecond - pState.speedMetersPerSecond
                val limitedSpeed = pState.speedMetersPerSecond + 
                    if (maxSpeedChange.isFinite()) speedChange.coerceIn(-maxSpeedChange, maxSpeedChange) else speedChange

                // Limit steering velocity and acceleration
                val targetAngleRad = tState.angle.radians
                val prevAngleRad = pState.angle.radians

                // Compute shortest angle difference
                val angleDiff = com.areslib.math.InputMath.wrapAngle(targetAngleRad - prevAngleRad)

                // Target steering velocity
                val targetSteerVel = angleDiff / dtSeconds
                // Clamp steering velocity
                val limitSteerVelVal = kotlin.math.abs(maxSteerVelRadPerSec)
                val limitedSteerVel = if (limitSteerVelVal.isFinite()) {
                    targetSteerVel.coerceIn(-limitSteerVelVal, limitSteerVelVal)
                } else {
                    targetSteerVel
                }

                // Clamp steering acceleration
                val prevSteerVel = previousSteerVels[i]
                val steerVelChange = limitedSteerVel - prevSteerVel
                val rawMaxSteerVelChange = maxSteerAccelRadPerSec2 * dtSeconds
                val maxSteerVelChange = kotlin.math.abs(rawMaxSteerVelChange)
                val finalSteerVel = prevSteerVel + 
                    if (maxSteerVelChange.isFinite()) steerVelChange.coerceIn(-maxSteerVelChange, maxSteerVelChange) else steerVelChange

                previousSteerVels[i] = finalSteerVel

                val finalAngle = prevAngleRad + finalSteerVel * dtSeconds
                limitedStatesBuffer[i] = SwerveModuleState(limitedSpeed, Rotation2d(finalAngle))
            }
            limitedStatesBuffer
        } else {
            targetStatesBuffer
        }

        previousStates = limitedStates
        return limitedStates
    }

    /**
     * Computes static steering angles pointing each module towards the center of the robot
     * (forming an "X" layout) with exactly 0.0 velocity. This locked state provides maximum
     * physical resistance against external defensive pushing from other robots.
     */
    fun toXLockStates(): Array<SwerveModuleState> {
        val states = Array(numModules) { i ->
            val module = moduleTranslations[i]
            val angle = atan2(module.y, module.x)
            SwerveModuleState(0.0, Rotation2d(angle))
        }
        previousStates = states
        return states
    }
}

