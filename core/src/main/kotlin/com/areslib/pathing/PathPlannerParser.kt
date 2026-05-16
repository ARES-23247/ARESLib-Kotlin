package com.areslib.pathing

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import kotlin.math.hypot

object PathPlannerParser {
    private val gson = Gson()

    /**
     * Parses a PathPlanner .path JSON string into an immutable Path.
     * Generates a dense array of interpolations using Cubic Bezier math.
     */
    fun parsePath(jsonString: String): Path {
        val root = gson.fromJson(jsonString, JsonObject::class.java)
        
        val waypointsArray = root.getAsJsonArray("waypoints")
        val pathPoints = mutableListOf<PathPoint>()
        var accumulatedDistance = 0.0

        val parsedWaypoints = mutableListOf<WaypointData>()
        for (i in 0 until waypointsArray.size()) {
            val wp = waypointsArray.get(i).asJsonObject
            val anchorNode = wp.getAsJsonObject("anchor")
            val anchor = com.areslib.math.Translation2d(anchorNode.get("x").asDouble, anchorNode.get("y").asDouble)
            
            val prevNode = if (wp.has("prevControl") && !wp.get("prevControl").isJsonNull) wp.getAsJsonObject("prevControl") else null
            val prevControl = prevNode?.let { com.areslib.math.Translation2d(it.get("x").asDouble, it.get("y").asDouble) } ?: anchor
            
            val nextNode = if (wp.has("nextControl") && !wp.get("nextControl").isJsonNull) wp.getAsJsonObject("nextControl") else null
            val nextControl = nextNode?.let { com.areslib.math.Translation2d(it.get("x").asDouble, it.get("y").asDouble) } ?: anchor
            
            parsedWaypoints.add(WaypointData(anchor, prevControl, nextControl))
        }

        if (parsedWaypoints.isEmpty()) {
            return Path(emptyList())
        }
        
        // Initial heading check
        var initialHeading = Rotation2d(0.0)
        if (parsedWaypoints.size > 1) {
            val wp1 = parsedWaypoints[0]
            val wp2 = parsedWaypoints[1]
            initialHeading = com.areslib.math.BezierSpline.evaluateHeading(wp1.anchor, wp1.nextControl, wp2.prevControl, wp2.anchor, 0.0)
        }

        // Add the very first point
        pathPoints.add(
            PathPoint(
                pose = Pose2d(parsedWaypoints[0].anchor.x, parsedWaypoints[0].anchor.y, initialHeading),
                velocityMps = 1.0,
                distanceMeters = 0.0
            )
        )

        // For each segment between two waypoints, generate the spline
        for (i in 0 until parsedWaypoints.size - 1) {
            val wp1 = parsedWaypoints[i]
            val wp2 = parsedWaypoints[i + 1]
            
            // P0 = wp1.anchor, P1 = wp1.nextControl, P2 = wp2.prevControl, P3 = wp2.anchor
            // Generate dense points along the curve
            val numSamples = 20
            for (step in 1..numSamples) {
                val t = step.toDouble() / numSamples
                val point = com.areslib.math.BezierSpline.evaluate(wp1.anchor, wp1.nextControl, wp2.prevControl, wp2.anchor, t)
                val heading = com.areslib.math.BezierSpline.evaluateHeading(wp1.anchor, wp1.nextControl, wp2.prevControl, wp2.anchor, t)
                
                // Calculate distance from previous point
                val prevPathPoint = pathPoints.last()
                val dx = point.x - prevPathPoint.pose.x
                val dy = point.y - prevPathPoint.pose.y
                accumulatedDistance += hypot(dx, dy)
                
                pathPoints.add(
                    PathPoint(
                        pose = Pose2d(point.x, point.y, heading),
                        velocityMps = 1.0, // Constant for now, profiling is Phase 19
                        distanceMeters = accumulatedDistance
                    )
                )
            }
        }

        return Path(pathPoints)
    }

    private data class WaypointData(
        val anchor: com.areslib.math.Translation2d,
        val prevControl: com.areslib.math.Translation2d,
        val nextControl: com.areslib.math.Translation2d
    )
}
