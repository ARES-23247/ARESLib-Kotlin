package com.areslib.pathing

import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Translation2d
import kotlin.math.hypot

/**
 * Utility for generating smooth, continuous trajectories on the fly.
 * Uses cubic Bezier splines and simple trapezoidal motion profiling.
 */
object TrajectoryGenerator {
    
    /**
     * Constraints for generating the motion profile.
     */
    data class PathConstraints(
        val maxVelocityMps: Double,
        val maxAccelerationMps2: Double
    )

    /**
     * Generates a single-segment path from a starting pose to a target pose on the fly.
     * Uses a Cubic Bezier curve to smoothly interpolate position and heading.
     * 
     * @param startPose Current robot pose
     * @param endPose Desired target pose
     * @param constraints Velocity and acceleration bounds
     */
    fun generateTrajectory(
        startPose: Pose2d,
        endPose: Pose2d,
        constraints: PathConstraints
    ): Path {
        val p0 = Translation2d(startPose.x, startPose.y)
        val p3 = Translation2d(endPose.x, endPose.y)
        
        val chordDist = hypot(p3.x - p0.x, p3.y - p0.y)
        if (chordDist < 0.01) {
            // Already at destination
            return Path(listOf(PathPoint(endPose, 0.0, 0.0)))
        }

        // Control point scaling factor for a smooth curve (usually 1/3 to 1/2 of chord length)
        val scaling = (chordDist / 3.0).coerceAtLeast(0.05)

        val p1 = Translation2d(
            p0.x + scaling * startPose.heading.cos,
            p0.y + scaling * startPose.heading.sin
        )
        val p2 = Translation2d(
            p3.x - scaling * endPose.heading.cos,
            p3.y - scaling * endPose.heading.sin
        )

        // Fixed sample count for zero-allocation budgets
        val numSamples = 50
        val points = mutableListOf<PathPoint>()
        var accumulatedDistance = 0.0
        var prevPoint = p0

        // Forward pass: Generate geometry and naive velocity
        for (step in 0..numSamples) {
            val t = step.toDouble() / numSamples
            val point = BezierSpline.evaluate(p0, p1, p2, p3, t)
            val heading = BezierSpline.evaluateHeading(p0, p1, p2, p3, t)
            
            val dist = hypot(point.x - prevPoint.x, point.y - prevPoint.y)
            accumulatedDistance += dist
            
            points.add(
                PathPoint(
                    pose = Pose2d(point.x, point.y, heading),
                    velocityMps = constraints.maxVelocityMps,
                    distanceMeters = accumulatedDistance,
                    curvature = 0.0,
                    tangentRadians = heading.radians
                )
            )
            prevPoint = point
        }

        // Backward pass: Apply trapezoidal deceleration (v_f^2 = v_i^2 + 2ad)
        points.last().velocityMps = 0.0
        for (i in points.size - 2 downTo 0) {
            val curr = points[i]
            val next = points[i + 1]
            val dist = next.distanceMeters - curr.distanceMeters
            val maxReachableVel = kotlin.math.sqrt(next.velocityMps * next.velocityMps + 2.0 * constraints.maxAccelerationMps2 * dist)
            curr.velocityMps = kotlin.math.min(curr.velocityMps, maxReachableVel)
        }

        // Forward pass: Apply trapezoidal acceleration
        points.first().velocityMps = 0.0
        for (i in 1 until points.size) {
            val curr = points[i]
            val prev = points[i - 1]
            val dist = curr.distanceMeters - prev.distanceMeters
            val maxReachableVel = kotlin.math.sqrt(prev.velocityMps * prev.velocityMps + 2.0 * constraints.maxAccelerationMps2 * dist)
            curr.velocityMps = kotlin.math.min(curr.velocityMps, maxReachableVel)
        }

        return Path(points, emptyList())
    }
}
