package com.areslib.control

import com.areslib.math.Pose2d
import com.areslib.math.ChassisSpeeds
import com.areslib.math.Translation2d
import kotlin.math.*

/**
 * Pre-allocated result container for zero-allocation ShotSetup calculations.
 */
class ShotResult {
    var virtualTargetX: Double = 0.0
    var virtualTargetY: Double = 0.0
    var aimAngleRad: Double = 0.0
    var robotTargetHeadingRad: Double = 0.0
    var aimDistanceMeters: Double = 0.0
    var targetFlywheelRpm: Double = 0.0
    var targetCowlAngleDegrees: Double = 0.0
    var angularVelocityFeedforwardRadPerSec: Double = 0.0
}

/**
 * Pure functional lookahead coordinate solver for Shoot-on-the-Move (SOTM).
 * Computes exact target flywheel RPM, cowl angle, and angular velocity feedforward.
 */
object ShotSetup {
    private val TOF_KEYS = doubleArrayOf(1.24, 2.0, 3.0, 4.0, 5.6)
    private val TOF_VALUES = doubleArrayOf(0.128, 0.212, 0.345, 0.481, 0.795)

    private val SHOT_KEYS = doubleArrayOf(
        1.24, 2.0, 2.2, 2.5, 3.0, 3.2, 3.4, 3.63, 3.80, 4.0, 4.2, 4.4, 4.6, 4.8, 5.0, 5.2, 5.4, 5.6
    )
    private val SHOT_RPM = doubleArrayOf(
        3350.0, 3400.0, 3450.0, 3500.0, 3550.0, 3600.0, 3650.0, 3700.0, 3750.0, 3800.0, 3850.0, 3900.0, 3950.0, 4000.0, 4050.0, 4100.0, 4150.0, 4200.0
    )
    private val SHOT_COWL = doubleArrayOf(
        0.50, 0.70, 0.80, 0.95, 1.10, 1.15, 1.20, 1.25, 1.30, 1.35, 1.40, 1.45, 1.50, 1.55, 1.60, 1.65, 1.70, 1.75
    )

    // Marvin 19 has the shooter offset at the back of the chassis
    private const val SHOOTER_OFFSET_X = -0.044704
    private const val SHOOTER_OFFSET_Y = -0.055626

    fun interpolateTof(distance: Double): Double {
        if (distance <= TOF_KEYS.first()) return TOF_VALUES.first()
        if (distance >= TOF_KEYS.last()) return TOF_VALUES.last()
        for (i in 0 until TOF_KEYS.size - 1) {
            if (distance >= TOF_KEYS[i] && distance <= TOF_KEYS[i + 1]) {
                val t = (distance - TOF_KEYS[i]) / (TOF_KEYS[i + 1] - TOF_KEYS[i])
                return TOF_VALUES[i] + t * (TOF_VALUES[i + 1] - TOF_VALUES[i])
            }
        }
        return TOF_VALUES.last()
    }

    fun interpolateRpm(distance: Double): Double {
        if (distance <= SHOT_KEYS.first()) return SHOT_RPM.first()
        if (distance >= SHOT_KEYS.last()) return SHOT_RPM.last()
        for (i in 0 until SHOT_KEYS.size - 1) {
            if (distance >= SHOT_KEYS[i] && distance <= SHOT_KEYS[i + 1]) {
                val t = (distance - SHOT_KEYS[i]) / (SHOT_KEYS[i + 1] - SHOT_KEYS[i])
                return SHOT_RPM[i] + t * (SHOT_RPM[i + 1] - SHOT_RPM[i])
            }
        }
        return SHOT_RPM.last()
    }

    fun interpolateCowl(distance: Double): Double {
        if (distance <= SHOT_KEYS.first()) return SHOT_COWL.first()
        if (distance >= SHOT_KEYS.last()) return SHOT_COWL.last()
        for (i in 0 until SHOT_KEYS.size - 1) {
            if (distance >= SHOT_KEYS[i] && distance <= SHOT_KEYS[i + 1]) {
                val t = (distance - SHOT_KEYS[i]) / (SHOT_KEYS[i + 1] - SHOT_KEYS[i])
                return SHOT_COWL[i] + t * (SHOT_COWL[i + 1] - SHOT_COWL[i])
            }
        }
        return SHOT_COWL.last()
    }

    /**
     * Performs a latency-compensated iterative convergence calculation for SOTM.
     * Populates [result] to prevent runtime allocations.
     * @param robotPose Current robot position and orientation on the field
     * @param fieldCentricSpeeds Current velocity vector of the chassis in field coordinates
     * @param target Field coordinates of the goal (Speaker)
     * @param result Pre-allocated output container
     */
    fun calculate(
        robotPose: Pose2d,
        fieldCentricSpeeds: ChassisSpeeds,
        target: Translation2d,
        result: ShotResult
    ) {
        val dtDelay = 0.05 // 50ms total delay compensation
        
        // 1. Compute phase delay compensated chassis position and heading
        val compHeading = robotPose.heading.radians + fieldCentricSpeeds.omegaRadiansPerSecond * dtDelay
        val compX = robotPose.x + fieldCentricSpeeds.vxMetersPerSecond * dtDelay
        val compY = robotPose.y + fieldCentricSpeeds.vyMetersPerSecond * dtDelay

        // 2. Translate center to shooter offset based on heading rotation
        val cosH = cos(compHeading)
        val sinH = sin(compHeading)
        val rotOffsetX = SHOOTER_OFFSET_X * cosH - SHOOTER_OFFSET_Y * sinH
        val rotOffsetY = SHOOTER_OFFSET_X * sinH + SHOOTER_OFFSET_Y * cosH
        
        val shooterX = compX + rotOffsetX
        val shooterY = compY + rotOffsetY

        // 3. Field-relative shooter velocity vector (translation + rotational cross product)
        val shooterVx = fieldCentricSpeeds.vxMetersPerSecond - fieldCentricSpeeds.omegaRadiansPerSecond * rotOffsetY
        val shooterVy = fieldCentricSpeeds.vyMetersPerSecond + fieldCentricSpeeds.omegaRadiansPerSecond * rotOffsetX

        // 4. Iterative solver for lookahead distance (5 loops)
        var virtualTargetX = target.x
        var virtualTargetY = target.y
        var tof: Double
        var aimDistance: Double

        for (i in 0 until 5) {
            val dx = virtualTargetX - shooterX
            val dy = virtualTargetY - shooterY
            aimDistance = hypot(dx, dy)
            tof = interpolateTof(aimDistance)
            virtualTargetX = target.x - shooterVx * tof
            virtualTargetY = target.y - shooterVy * tof
        }

        // 5. Final coordinates and aiming target heading calculations
        val dxFinal = virtualTargetX - shooterX
        val dyFinal = virtualTargetY - shooterY
        aimDistance = hypot(dxFinal, dyFinal)
        
        val aimAngle = atan2(dyFinal, dxFinal)
        
        // Shooter is at the back facing rearward, so the robot's front is 180 degrees away
        val robotTargetHeading = aimAngle + PI
        
        val wrappedRobotHeading = com.areslib.math.InputMath.wrapAngle(robotTargetHeading)

        // 6. Direct derivative for exact heading angular velocity feedforward
        val angularVelFF = if (aimDistance > 0.05) {
            (-dxFinal * shooterVy + dyFinal * shooterVx) / (aimDistance * aimDistance)
        } else {
            0.0
        }

        // 7. Map lookahead aimDistance to flywheel and cowl parameters
        val targetRpm = interpolateRpm(aimDistance)
        val targetCowl = interpolateCowl(aimDistance)

        // Write outputs
        result.virtualTargetX = virtualTargetX
        result.virtualTargetY = virtualTargetY
        result.aimAngleRad = aimAngle
        result.robotTargetHeadingRad = wrappedRobotHeading
        result.aimDistanceMeters = aimDistance
        result.targetFlywheelRpm = targetRpm
        result.targetCowlAngleDegrees = targetCowl
        result.angularVelocityFeedforwardRadPerSec = angularVelFF
    }
}
