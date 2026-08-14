package com.areslib.math.kinematics

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometric and inertial properties of a 2-degree-of-freedom planar serial linkage or articulated arm.
 *
 * All lengths are in meters, masses in kilograms, and gravity in m/s².
 */
data class TwoDofLinkageParameters(
    /** Length of the proximal link (base to elbow) in meters. */
    val l1: Double,
    /** Length of the distal link (elbow to end-effector) in meters. */
    val l2: Double,
    /** Mass of the proximal link in kilograms. */
    val m1: Double,
    /** Mass of the distal link in kilograms. */
    val m2: Double,
    /** Center of mass distance along link 1 from joint 1 in meters. Defaults to mid-link. */
    val rc1: Double = l1 / 2.0,
    /** Center of mass distance along link 2 from joint 2 in meters. Defaults to mid-link. */
    val rc2: Double = l2 / 2.0,
    /** Acceleration due to gravity in m/s². Defaults to standard Earth gravity (9.80665). */
    val g: Double = 9.80665,
) {
    init {
        require(l1 > 0.0) { "Link 1 length must be strictly positive" }
        require(l2 > 0.0) { "Link 2 length must be strictly positive" }
        require(m1 >= 0.0) { "Link 1 mass cannot be negative" }
        require(m2 >= 0.0) { "Link 2 mass cannot be negative" }
    }

    /** Maximum reach radius of the linkage from base origin in meters. */
    val maxReach: Double get() = l1 + l2

    /** Minimum reach radius (inner workspace boundary) in meters. */
    val minReach: Double get() = kotlin.math.abs(l1 - l2)
}

/** Joint angle coordinates in radians. */
data class LinkageJointAngles(
    /** Base joint angle in radians relative to horizontal ground (+X axis). CCW positive. */
    val theta1Rad: Double,
    /** Elbow joint angle in radians relative to Link 1 axis. CCW positive. */
    val theta2Rad: Double,
)

/** End-effector Cartesian coordinates in meters. */
data class LinkageEndEffectorPose(
    val x: Double,
    val y: Double,
)

/** Geometric branch selection for inverse kinematics solutions. */
enum class ElbowConfiguration {
    ELBOW_UP,
    ELBOW_DOWN,
}

/**
 * Analytical kinematics, Jacobian singularity checking, and Lagrangian gravity feedforward solver
 * for 2-DOF planar linkages and articulated robotic arms.
 */
class TwoDofLinkageKinematics(val params: TwoDofLinkageParameters) {

    /**
     * Computes the Cartesian (X, Y) position of the end-effector from joint angles.
     *
     * X = L1 * cos(theta1) + L2 * cos(theta1 + theta2)
     * Y = L1 * sin(theta1) + L2 * sin(theta1 + theta2)
     */
    fun forwardKinematics(theta1: Double, theta2: Double): LinkageEndEffectorPose {
        val x = params.l1 * cos(theta1) + params.l2 * cos(theta1 + theta2)
        val y = params.l1 * sin(theta1) + params.l2 * sin(theta1 + theta2)
        return LinkageEndEffectorPose(x, y)
    }

    /**
     * Checks whether a target (X, Y) point lies within the physical reachability envelope.
     */
    fun isReachable(x: Double, y: Double): Boolean {
        val rSq = x * x + y * y
        val minR = params.minReach
        val maxR = params.maxReach
        return rSq >= (minR * minR - 1e-7) && rSq <= (maxR * maxR + 1e-7)
    }

    /**
     * Computes analytical inverse kinematics for a target (X, Y) coordinate.
     *
     * @param x Target X position in meters.
     * @param y Target Y position in meters.
     * @param config Geometric solution branch (Elbow Up vs Elbow Down).
     * @return [LinkageJointAngles] if point is reachable, or null if target is outside workspace.
     */
    fun inverseKinematics(x: Double, y: Double, config: ElbowConfiguration = ElbowConfiguration.ELBOW_UP): LinkageJointAngles? {
        val rSq = x * x + y * y
        val l1 = params.l1
        val l2 = params.l2

        val cosTheta2 = (rSq - l1 * l1 - l2 * l2) / (2.0 * l1 * l2)
        if (cosTheta2 < -1.0 - 1e-6 || cosTheta2 > 1.0 + 1e-6) {
            return null
        }
        val clampedCos = cosTheta2.coerceIn(-1.0, 1.0)
        val sinTheta2Mag = sqrt(1.0 - clampedCos * clampedCos)

        val theta2 = when (config) {
            ElbowConfiguration.ELBOW_UP -> -atan2(sinTheta2Mag, clampedCos)
            ElbowConfiguration.ELBOW_DOWN -> atan2(sinTheta2Mag, clampedCos)
        }

        val k1 = l1 + l2 * cos(theta2)
        val k2 = l2 * sin(theta2)
        val gamma = atan2(k2, k1)
        val theta1 = atan2(y, x) - gamma

        return LinkageJointAngles(theta1, theta2)
    }

    /**
     * Computes the 2x2 geometric Jacobian matrix J(theta) mapping joint velocities to end-effector velocities:
     *
     * [vx; vy] = J * [theta1_dot; theta2_dot]
     */
    fun jacobian(theta1: Double, theta2: Double): Array<DoubleArray> {
        val l1 = params.l1
        val l2 = params.l2
        val s1 = sin(theta1)
        val c1 = cos(theta1)
        val s12 = sin(theta1 + theta2)
        val c12 = cos(theta1 + theta2)

        val j11 = -l1 * s1 - l2 * s12
        val j12 = -l2 * s12
        val j21 = l1 * c1 + l2 * c12
        val j22 = l2 * c12

        return arrayOf(
            doubleArrayOf(j11, j12),
            doubleArrayOf(j21, j22)
        )
    }

    /**
     * Computes the determinant of the Jacobian matrix det(J) = L1 * L2 * sin(theta2).
     *
     * Singularity occurs when theta2 is 0 or ±π (arm fully outstretched or fully folded back).
     */
    fun jacobianDeterminant(theta2: Double): Double {
        return params.l1 * params.l2 * sin(theta2)
    }

    /**
     * Detects if the mechanism is operating in or near a kinematic singularity where joint velocities diverge.
     */
    fun isNearSingularity(theta1: Double, theta2: Double, threshold: Double = 0.05): Boolean {
        val det = kotlin.math.abs(jacobianDeterminant(theta2))
        return det < threshold * params.l1 * params.l2
    }

    /**
     * Computes analytical Lagrangian multivariable continuous gravity compensation torque vector G(theta).
     *
     * G1 = (m1 * rc1 + m2 * L1) * g * cos(theta1) + m2 * rc2 * g * cos(theta1 + theta2)
     * G2 = m2 * rc2 * g * cos(theta1 + theta2)
     *
     * @return DoubleArray of size 2 containing [torque1_Nm, torque2_Nm].
     */
    fun gravityTorque(theta1: Double, theta2: Double): DoubleArray {
        val c1 = cos(theta1)
        val c12 = cos(theta1 + theta2)
        val g = params.g

        val g1 = (params.m1 * params.rc1 + params.m2 * params.l1) * g * c1 +
            (params.m2 * params.rc2) * g * c12
        val g2 = (params.m2 * params.rc2) * g * c12

        return doubleArrayOf(g1, g2)
    }
}
