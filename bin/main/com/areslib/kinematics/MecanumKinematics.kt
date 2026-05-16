package com.areslib.kinematics

import com.areslib.math.ChassisSpeeds

/**
 * Pure math class for calculating Mecanum wheel speeds from ChassisSpeeds.
 */
class MecanumKinematics(
    private val trackWidthMeters: Double,
    private val wheelBaseMeters: Double
) {
    // The sum of the half-track-width and half-wheel-base is the effective moment arm for rotation.
    private val k: Double = (trackWidthMeters / 2.0) + (wheelBaseMeters / 2.0)

    /**
     * Converts robot-centric ChassisSpeeds into individual MecanumWheelSpeeds.
     */
    fun toWheelSpeeds(speeds: ChassisSpeeds): MecanumWheelSpeeds {
        val vx = speeds.vxMetersPerSecond
        val vy = speeds.vyMetersPerSecond
        val omega = speeds.omegaRadiansPerSecond
        
        // Mecanum inverse kinematics equations
        // FL = vx - vy - omega * k
        // FR = vx + vy + omega * k
        // BL = vx + vy - omega * k
        // BR = vx - vy + omega * k
        // Note: Standard sign conventions may vary. Assuming Y is left and X is forward.
        
        val fl = vx - vy - omega * k
        val fr = vx + vy + omega * k
        val bl = vx + vy - omega * k
        val br = vx - vy + omega * k
        
        return MecanumWheelSpeeds(fl, fr, bl, br)
    }
}
