package com.areslib.pathing

import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import com.areslib.math.geometry.Translation2d
import kotlin.math.hypot

/**
 * Object implementation for Detour Generator.
 *
 * Autonomous path planning, trajectory generation, and obstacle avoidance module.
 *
 * ### Coordinate System:
 * Field-centric coordinates in meters ($m$) relative to field origin.
 */
object DetourGenerator {

    /**
     * Generates a smooth, coordinate-snap-free transition path from the robot's active real-time state
     * to intercept a target detour path, then stitches the remaining detour path points.
     */
    fun generateTangentArc(
        startPose: Pose2d,
        currentVelMps: Double,
        targetPath: Path,
        interceptLookaheadMeters: Double = 1.0,
        maxVelocityMps: Double = 2.0
    ): Path {
        if (targetPath.points.isEmpty()) return Path(emptyList())

        // 1. Find the intercept point on the target path
        // For simplicity, we choose the point closest to lookahead distance, or the start point if the path is short.
        val interceptDist = interceptLookaheadMeters.coerceIn(0.0, targetPath.points.last().distanceMeters)
        val interceptPoint = targetPath.sampleAtDistance(interceptDist)

        // 2. Generate a Cubic Bezier transition curve from startPose to interceptPoint
        val p0 = Translation2d(startPose.x, startPose.y)
        val p3 = Translation2d(interceptPoint.pose.x, interceptPoint.pose.y)
        val chordDist = hypot(p3.x - p0.x, p3.y - p0.y)

        // Control point scaling factor (1/3 of chord length is standard for smooth cubic splines)
        val scaling = (chordDist / 3.0).coerceAtLeast(0.1)

        val p1 = Translation2d(
            p0.x + scaling * startPose.heading.cos,
            p0.y + scaling * startPose.heading.sin
        )
        val p2 = Translation2d(
            p3.x - scaling * interceptPoint.pose.heading.cos,
            p3.y - scaling * interceptPoint.pose.heading.sin
        )

        val numSamples = 15
        val transitionPoints = mutableListOf<PathPoint>()
        var accumulatedDistance = 0.0

        // Add start point
        transitionPoints.add(
            PathPoint(
                pose = startPose,
                velocityMps = currentVelMps,
                distanceMeters = 0.0
            )
        )

        for (step in 1..numSamples) {
            val t = step.toDouble() / numSamples
            val point = BezierSpline.evaluate(p0, p1, p2, p3, t)
            val heading = BezierSpline.evaluateHeading(p0, p1, p2, p3, t)

            val prevPt = transitionPoints.last()
            val dx = point.x - prevPt.pose.x
            val dy = point.y - prevPt.pose.y
            accumulatedDistance += hypot(dx, dy)

            // Interpolate velocity from current speed to target intercept speed
            val tau = t
            val targetVel = (1.0 - tau) * currentVelMps + tau * interceptPoint.velocityMps

            transitionPoints.add(
                PathPoint(
                    pose = Pose2d(point.x, point.y, heading),
                    velocityMps = targetVel.coerceIn(0.1, maxVelocityMps),
                    distanceMeters = accumulatedDistance
                )
            )
        }

        // 3. Stitched remaining detour points (from the intercept distance onward)
        val finalStitchedPoints = transitionPoints.toMutableList()
        val transitionEndDistance = finalStitchedPoints.last().distanceMeters

        // Add remaining points of targetPath, shifting their distanceMeters
        for (i in 0 until targetPath.points.size) {
            val pt = targetPath.points[i]
            if (pt.distanceMeters > interceptDist) {
                val relativeDistance = pt.distanceMeters - interceptDist
                finalStitchedPoints.add(
                    PathPoint(
                        pose = pt.pose,
                        velocityMps = pt.velocityMps,
                        distanceMeters = transitionEndDistance + relativeDistance,
                        curvature = pt.curvature
                    )
                )
            }
        }

        // 4. Stitched path events
        val finalStitchedEvents = ArrayList<PathEvent>()
        for (i in 0 until targetPath.events.size) {
            val event = targetPath.events[i]
            if (event.triggerDistanceMeters > interceptDist) {
                finalStitchedEvents.add(
                    PathEvent(
                        event.eventName,
                        transitionEndDistance + (event.triggerDistanceMeters - interceptDist)
                    )
                )
            }
        }

        return Path(finalStitchedPoints, finalStitchedEvents)
    }
}
