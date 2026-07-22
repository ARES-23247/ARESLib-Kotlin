package com.areslib.kinematics

import com.areslib.math.geometry.ChassisSpeeds

/**
 * Mecanum Drivetrain Forward and Inverse Kinematics Calculator.
 *
 * Converts robot-frame velocities $[v_x, v_y, \omega]$ into 4 individual wheel surface speeds ($v_{FL}, v_{FR}, v_{BL}, v_{BR}$)
 * and vice versa using the physical drivetrain geometry footprint.
 *
 * ### Inverse Kinematics Equations:
 * For effective rotation moment arm $k = \frac{\text{trackWidth}}{2} + \frac{\text{wheelBase}}{2}$:
 * $$v_{FL} = v_x - v_y - \omega \cdot k$$
 * $$v_{FR} = v_x + v_y + \omega \cdot k$$
 * $$v_{BL} = v_x + v_y - \omega \cdot k$$
 * $$v_{BR} = v_x - v_y + \omega \cdot k$$
 *
 * ### Forward Kinematics Equations:
 * $$v_x = \frac{v_{FL} + v_{FR} + v_{BL} + v_{BR}}{4}$$
 * $$v_y = \frac{-v_{FL} + v_{FR} + v_{BL} - v_{BR}}{4}$$
 * $$\omega = \frac{-v_{FL} + v_{FR} - v_{BL} + v_{BR}}{4 \cdot k}$$
 *
 * ### Physical Units & Coordinates:
 * - Linear Dimensions: Meters ($m$)
 * - Linear Velocities: Meters per second ($m/s$)
 * - Angular Velocities: Radians per second ($rad/s$), counter-clockwise positive
 *
 * @param trackWidthMeters Distance between left and right wheel centers in meters.
 * @param wheelBaseMeters Distance between front and rear wheel centers in meters.
 */
class MecanumKinematics(
    private val trackWidthMeters: Double,
    private val wheelBaseMeters: Double
) {
    /** The effective rotational moment arm constant $k = (W/2) + (L/2)$ in meters. */
    val k: Double = (trackWidthMeters / 2.0) + (wheelBaseMeters / 2.0)

    /**
     * Converts robot-centric [ChassisSpeeds] into individual [MecanumWheelSpeeds].
     *
     * @param speeds Desired robot-frame velocities $[v_x, v_y, \omega]$ (m/s, rad/s).
     * @return Calculated [MecanumWheelSpeeds] (m/s).
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
     * Converts individual wheel surface speeds into robot-frame [ChassisSpeeds].
     */
    fun toChassisSpeeds(fl: Double, fr: Double, bl: Double, br: Double): ChassisSpeeds {
        val vx = (fl + fr + bl + br) / 4.0
        val vy = (-fl + fr + bl - br) / 4.0
        val omega = (-fl + fr - bl + br) / (4.0 * k)
        return ChassisSpeeds(vx, vy, omega)
    }

    /**
     * Zero-GC variant converting primitive velocity parameters into a pre-allocated 4-element output array.
     *
     * @param vx Robot forward velocity in m/s.
     * @param vy Robot strafe velocity (left positive) in m/s.
     * @param omega Robot rotation rate (CCW positive) in rad/s.
     * @param outSpeeds Output 4-element array storing $[v_{FL}, v_{FR}, v_{BL}, v_{BR}]$.
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
         * Normalizes wheel speeds in-place if any wheel speed magnitude exceeds the maximum allowed speed limit.
         *
         * @param speeds 4-element array of wheel speeds $[v_{FL}, v_{FR}, v_{BL}, v_{BR}]$ (m/s).
         * @param maxSpeedMetersPerSecond Maximum allowed wheel surface speed in m/s.
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

            var maxMagnitude = 0.0
            for (i in 0..3) {
                val absSpeed = kotlin.math.abs(speeds[i])
                if (absSpeed > maxMagnitude) {
                    maxMagnitude = absSpeed
                }
            }

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
