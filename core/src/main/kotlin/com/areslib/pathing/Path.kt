package com.areslib.pathing

import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import kotlin.math.hypot

/**
 * Represents a single point along a trajectory path.
 */
data class PathPoint(
    var pose: Pose2d,
    var velocityMps: Double,
    var distanceMeters: Double = 0.0,
    var curvature: Double = 0.0
)

class MutablePathPoint {
    var x: Double = 0.0
    var y: Double = 0.0
    var headingRad: Double = 0.0
    var velocityMps: Double = 0.0
    var distanceMeters: Double = 0.0
    var curvature: Double = 0.0

    fun toPathPoint(): PathPoint = PathPoint(
        Pose2d(x, y, Rotation2d(headingRad)),
        velocityMps,
        distanceMeters,
        curvature
    )

    fun copyInto(out: PathPoint) {
        out.pose.x = x
        out.pose.y = y
        out.pose.heading.rawRadians = headingRad
        out.velocityMps = velocityMps
        out.distanceMeters = distanceMeters
        out.curvature = curvature
    }
}

/**
 * An immutable data structure representing a parsed trajectory.
 */
data class Path(
    val points: List<PathPoint>,
    val events: List<PathEvent> = emptyList()
) {
    /**
     * Interpolates to find the target PathPoint at a given distance along the path.
     */
    fun sampleAtDistance(distanceMeters: Double): PathPoint {
        if (points.isEmpty()) return PathPoint(Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0)), 0.0)
        if (distanceMeters <= points.first().distanceMeters) return points.first()
        if (distanceMeters >= points.last().distanceMeters) return points.last()

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            if (distanceMeters >= p1.distanceMeters && distanceMeters <= p2.distanceMeters) {
                val denom = p2.distanceMeters - p1.distanceMeters
                val t = if (kotlin.math.abs(denom) < 1e-6) 0.0 else (distanceMeters - p1.distanceMeters) / denom
                
                val interpX = p1.pose.x + (p2.pose.x - p1.pose.x) * t
                val interpY = p1.pose.y + (p2.pose.y - p1.pose.y) * t
                
                // For simplicity, linearly interpolate heading. In a production system, use AngleMath.lerp.
                val deltaHeading = p2.pose.heading.radians - p1.pose.heading.radians
                // Normalize delta
                val normDelta = com.areslib.math.InputMath.wrapAngle(deltaHeading)
                
                val interpHeading = Rotation2d(p1.pose.heading.radians + normDelta * t)
                val interpVel = p1.velocityMps + (p2.velocityMps - p1.velocityMps) * t
                val interpCurvature = p1.curvature + (p2.curvature - p1.curvature) * t
                
                return PathPoint(
                    Pose2d(interpX, interpY, interpHeading),
                    interpVel,
                    distanceMeters,
                    interpCurvature
                )
            }
        }
        return points.last()
    }

    /**
     * Interpolates in-place to find the target state without heap allocations.
     */
    fun sampleAtDistance(distanceMeters: Double, out: MutablePathPoint) {
        if (points.isEmpty()) {
            out.x = 0.0; out.y = 0.0; out.headingRad = 0.0
            out.velocityMps = 0.0; out.distanceMeters = distanceMeters; out.curvature = 0.0
            return
        }
        if (distanceMeters <= points.first().distanceMeters) {
            val first = points.first()
            out.x = first.pose.x; out.y = first.pose.y; out.headingRad = first.pose.heading.radians
            out.velocityMps = first.velocityMps; out.distanceMeters = first.distanceMeters; out.curvature = first.curvature
            return
        }
        if (distanceMeters >= points.last().distanceMeters) {
            val last = points.last()
            out.x = last.pose.x; out.y = last.pose.y; out.headingRad = last.pose.heading.radians
            out.velocityMps = last.velocityMps; out.distanceMeters = last.distanceMeters; out.curvature = last.curvature
            return
        }

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            if (distanceMeters >= p1.distanceMeters && distanceMeters <= p2.distanceMeters) {
                val denom = p2.distanceMeters - p1.distanceMeters
                val t = if (kotlin.math.abs(denom) < 1e-6) 0.0 else (distanceMeters - p1.distanceMeters) / denom
                
                out.x = p1.pose.x + (p2.pose.x - p1.pose.x) * t
                out.y = p1.pose.y + (p2.pose.y - p1.pose.y) * t
                
                val deltaHeading = p2.pose.heading.radians - p1.pose.heading.radians
                val normDelta = com.areslib.math.InputMath.wrapAngle(deltaHeading)
                out.headingRad = p1.pose.heading.radians + normDelta * t
                
                out.velocityMps = p1.velocityMps + (p2.velocityMps - p1.velocityMps) * t
                out.distanceMeters = distanceMeters
                out.curvature = p1.curvature + (p2.curvature - p1.curvature) * t
                return
            }
        }
        val last = points.last()
        out.x = last.pose.x; out.y = last.pose.y; out.headingRad = last.pose.heading.radians
        out.velocityMps = last.velocityMps; out.distanceMeters = last.distanceMeters; out.curvature = last.curvature
    }
}
