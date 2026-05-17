package com.areslib.math

import kotlin.math.atan2

/**
 * Pure math utility for evaluating Cubic Bezier curves,
 * used to generate PathPlanner trajectories.
 */
object BezierSpline {
    
    /**
     * Evaluates a Cubic Bezier curve at parameter [t] (0.0 to 1.0).
     * 
     * @param p0 Anchor 1 (Start)
     * @param p1 Next Control 1
     * @param p2 Prev Control 2
     * @param p3 Anchor 2 (End)
     */
    fun evaluate(p0: Translation2d, p1: Translation2d, p2: Translation2d, p3: Translation2d, t: Double): Translation2d {
        val u = 1.0 - t
        val uu = u * u
        val uuu = uu * u
        val tt = t * t
        val ttt = tt * t

        val x = uuu * p0.x + 3 * uu * t * p1.x + 3 * u * tt * p2.x + ttt * p3.x
        val y = uuu * p0.y + 3 * uu * t * p1.y + 3 * u * tt * p2.y + ttt * p3.y

        return Translation2d(x, y)
    }

    /**
     * Evaluates the first derivative of a Cubic Bezier curve at parameter [t] (0.0 to 1.0).
     * This represents the tangent vector (velocity direction).
     */
    fun evaluateDerivative(p0: Translation2d, p1: Translation2d, p2: Translation2d, p3: Translation2d, t: Double): Translation2d {
        val u = 1.0 - t
        val uu = u * u
        val tt = t * t

        // B'(t) = 3(1-t)^2(P1 - P0) + 6(1-t)t(P2 - P1) + 3t^2(P3 - P2)
        val term1X = 3 * uu * (p1.x - p0.x)
        val term1Y = 3 * uu * (p1.y - p0.y)

        val term2X = 6 * u * t * (p2.x - p1.x)
        val term2Y = 6 * u * t * (p2.y - p1.y)

        val term3X = 3 * tt * (p3.x - p2.x)
        val term3Y = 3 * tt * (p3.y - p2.y)

        return Translation2d(term1X + term2X + term3X, term1Y + term2Y + term3Y)
    }

    /**
     * Calculates the heading (rotation) tangent to the curve at parameter [t].
     */
    fun evaluateHeading(p0: Translation2d, p1: Translation2d, p2: Translation2d, p3: Translation2d, t: Double): Rotation2d {
        val derivative = evaluateDerivative(p0, p1, p2, p3, t)
        val headingRadians = atan2(derivative.y, derivative.x)
        return Rotation2d(headingRadians)
    }
}
