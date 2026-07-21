package com.areslib.kinematics

import com.areslib.math.geometry.ChassisSpeeds

/**
 * Pure math class for calculating Mecanum wheel speeds from ChassisSpeeds.
 */
class MecanumKinematics(
    private val trackWidthMeters: Double,
    private val wheelBaseMeters: Double
) {
    // The sum of the half-track-width and half-wheel-base is the effective moment arm for rotation.
    val k: Double = (trackWidthMeters / 2.0) + (wheelBaseMeters / 2.0)

    /**
     * Converts robot-centric ChassisSpeeds into individual MecanumWheelSpeeds.
     */
    fun toWheelSpeeds(speeds: ChassisSpeeds): MecanumWheelSpeeds {
        val vx = speeds.vxMetersPerSecond
        val vy = speeds.vyMetersPerSecond
        val omega = speeds.omegaRadiansPerSecond
        
        val fl = vx - vy - omega * k
        val fr = vx + vy + omega * k
        val bl = vx + vy - omega * k
        val br = vx - vy + omega * k
        
        return MecanumWheelSpeeds(fl, fr, bl, br)
    }

    /**
     * Converts robot-centric primitive velocities into individual wheel speeds in-place to avoid GC.
     */
    fun toWheelSpeeds(vx: Double, vy: Double, omega: Double, outSpeeds: DoubleArray) {
        if (outSpeeds.size < 4) return
        outSpeeds[0] = vx - vy - omega * k
        outSpeeds[1] = vx + vy + omega * k
        outSpeeds[2] = vx + vy - omega * k
        outSpeeds[3] = vx - vy + omega * k
    }

    companion object {
        /**
         * Normalizes wheel speeds in-place if any of them exceed the specified maximum speed.
         */
        fun normalize(speeds: DoubleArray, maxSpeedMetersPerSecond: Double) {
            if (speeds.size < 4) return
            if (maxSpeedMetersPerSecond <= 0.0 || maxSpeedMetersPerSecond.isNaN()) {
                speeds[0] = 0.0
                speeds[1] = 0.0
                speeds[2] = 0.0
                speeds[3] = 0.0
                return
            }
            val maxMagnitude = maxOf(
                kotlin.math.abs(speeds[0]),
                kotlin.math.abs(speeds[1]),
                kotlin.math.abs(speeds[2]),
                kotlin.math.abs(speeds[3])
            )
            
            if (maxMagnitude > maxSpeedMetersPerSecond) {
                val scale = maxSpeedMetersPerSecond / maxMagnitude
                speeds[0] *= scale
                speeds[1] *= scale
                speeds[2] *= scale
                speeds[3] *= scale
            }
        }
    }
}
