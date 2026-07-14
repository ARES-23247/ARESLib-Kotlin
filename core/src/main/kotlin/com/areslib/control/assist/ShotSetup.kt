package com.areslib.control.assist

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
 * Configuration for a robot's specific shooting geometry and ballistic tuning.
 *
 * Contains all the robot-specific constants needed for the Shoot-on-the-Move solver:
 * - Shooter offset from chassis center (back-mounted, side-mounted, etc.)
 * - Time-of-flight interpolation tables
 * - Flywheel RPM and cowl angle interpolation tables keyed by distance
 *
 * Each season's robot defines its own [ShotConfig] instance with its tuned values.
 *
 * @property shooterOffsetX X offset (meters) of the shooter relative to chassis center (positive = forward).
 * @property shooterOffsetY Y offset (meters) of the shooter relative to chassis center (positive = left).
 * @property tofKeys Distance breakpoints (meters) for time-of-flight interpolation, sorted ascending.
 * @property tofValues Time-of-flight values (seconds) corresponding to each distance breakpoint.
 * @property shotKeys Distance breakpoints (meters) for RPM and cowl interpolation, sorted ascending.
 * @property shotRpm Target flywheel RPM values corresponding to each shot distance breakpoint.
 * @property shotCowl Target cowl angle values (degrees) corresponding to each shot distance breakpoint.
 * @property delayCompensationSeconds Total latency compensation (seconds) for lookahead prediction.
 * @property shooterFacesRearward If true, the robot's front is 180° from the aim direction (back-mounted shooter).
 */
data class ShotConfig(
    val shooterOffsetX: Double,
    val shooterOffsetY: Double,
    val tofKeys: DoubleArray,
    val tofValues: DoubleArray,
    val shotKeys: DoubleArray,
    val shotRpm: DoubleArray,
    val shotCowl: DoubleArray,
    val delayCompensationSeconds: Double = 0.05,
    val shooterFacesRearward: Boolean = true
) {
    init {
        require(tofKeys.size == tofValues.size) { "tofKeys and tofValues must have the same length" }
        require(shotKeys.size == shotRpm.size) { "shotKeys and shotRpm must have the same length" }
        require(shotKeys.size == shotCowl.size) { "shotKeys and shotCowl must have the same length" }
    }
}

/**
 * Pure functional lookahead coordinate solver for Shoot-on-the-Move (SOTM).
 *
 * Computes exact target flywheel RPM, cowl angle, and angular velocity feedforward
 * using iterative latency-compensated lookahead convergence.
 *
 * This class is **robot-agnostic** — all robot-specific tuning constants are provided
 * via the [ShotConfig] parameter. The SOTM math algorithm is reusable across any
 * FTC or FRC robot with a turret/shooter mechanism.
 *
 * **Zero-GC compliance**: All calculations use primitives. The [ShotResult] output
 * container is pre-allocated by the caller and populated in-place.
 *
 * @param config Robot-specific shooting geometry and ballistic tuning tables.
 */
class ShotSetup(private val config: ShotConfig) {

    /**
     * Linearly interpolates the projectile time-of-flight (seconds) for a given aim distance (meters).
     */
    fun interpolateTof(distance: Double): Double {
        return interpolate(config.tofKeys, config.tofValues, distance)
    }

    /**
     * Linearly interpolates the target flywheel RPM for a given aim distance (meters).
     */
    fun interpolateRpm(distance: Double): Double {
        return interpolate(config.shotKeys, config.shotRpm, distance)
    }

    /**
     * Linearly interpolates the target cowl angle (degrees) for a given aim distance (meters).
     */
    fun interpolateCowl(distance: Double): Double {
        return interpolate(config.shotKeys, config.shotCowl, distance)
    }

    /**
     * Performs a latency-compensated iterative convergence calculation for SOTM.
     * Populates [result] in-place to prevent runtime allocations.
     *
     * @param robotPose Current robot position and orientation on the field.
     * @param fieldCentricSpeeds Current velocity vector of the chassis in field coordinates.
     * @param target Field coordinates of the goal (e.g., Speaker opening).
     * @param result Pre-allocated output container.
     */
    fun calculate(
        robotPose: Pose2d,
        fieldCentricSpeeds: ChassisSpeeds,
        target: Translation2d,
        result: ShotResult
    ) {
        val dtDelay = config.delayCompensationSeconds

        // 1. Compute phase delay compensated chassis position and heading
        val compHeading = robotPose.heading.radians + fieldCentricSpeeds.omegaRadiansPerSecond * dtDelay
        val compX = robotPose.x + fieldCentricSpeeds.vxMetersPerSecond * dtDelay
        val compY = robotPose.y + fieldCentricSpeeds.vyMetersPerSecond * dtDelay

        // 2. Translate center to shooter offset based on heading rotation
        val cosH = cos(compHeading)
        val sinH = sin(compHeading)
        val rotOffsetX = config.shooterOffsetX * cosH - config.shooterOffsetY * sinH
        val rotOffsetY = config.shooterOffsetX * sinH + config.shooterOffsetY * cosH

        val shooterX = compX + rotOffsetX
        val shooterY = compY + rotOffsetY

        // 3. Field-relative shooter velocity vector (translation + rotational cross product)
        val shooterVx = fieldCentricSpeeds.vxMetersPerSecond - fieldCentricSpeeds.omegaRadiansPerSecond * rotOffsetY
        val shooterVy = fieldCentricSpeeds.vyMetersPerSecond + fieldCentricSpeeds.omegaRadiansPerSecond * rotOffsetX

        // 4. Iterative solver for lookahead distance (5 loops)
        var virtualTargetX = target.x
        var virtualTargetY = target.y
        var aimDistance: Double

        for (i in 0 until 5) {
            val dx = virtualTargetX - shooterX
            val dy = virtualTargetY - shooterY
            aimDistance = hypot(dx, dy)
            val tof = interpolateTof(aimDistance)
            virtualTargetX = target.x - shooterVx * tof
            virtualTargetY = target.y - shooterVy * tof
        }

        // 5. Final coordinates and aiming target heading calculations
        val dxFinal = virtualTargetX - shooterX
        val dyFinal = virtualTargetY - shooterY
        aimDistance = hypot(dxFinal, dyFinal)

        val aimAngle = atan2(dyFinal, dxFinal)

        // Rearward-facing shooter: robot's front is 180° from the aim direction
        val robotTargetHeading = if (config.shooterFacesRearward) aimAngle + PI else aimAngle

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

    companion object {
        /**
         * Generic piecewise-linear interpolation for sorted key/value arrays.
         * Zero-allocation: operates on primitive arrays with indexed access.
         *
         * @param keys Sorted ascending breakpoint array.
         * @param values Corresponding output values.
         * @param x The input value to interpolate.
         * @return The interpolated output value.
         */
        fun interpolate(keys: DoubleArray, values: DoubleArray, x: Double): Double {
            if (x <= keys[0]) return values[0]
            if (x >= keys[keys.size - 1]) return values[values.size - 1]
            for (i in 0 until keys.size - 1) {
                if (x >= keys[i] && x <= keys[i + 1]) {
                    val t = (x - keys[i]) / (keys[i + 1] - keys[i])
                    return values[i] + t * (values[i + 1] - values[i])
                }
            }
            return values[values.size - 1]
        }
    }
}
