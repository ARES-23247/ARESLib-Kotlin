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
     * Note: This is a simplified parser that treats waypoints as linear path points
     * for MVP autonomy testing, rather than full spline generation.
     */
    fun parsePath(jsonString: String): Path {
        val root = gson.fromJson(jsonString, JsonObject::class.java)
        
        val waypointsArray = root.getAsJsonArray("waypoints")
        val pathPoints = mutableListOf<PathPoint>()
        var accumulatedDistance = 0.0

        for (i in 0 until waypointsArray.size()) {
            val wp = waypointsArray.get(i).asJsonObject
            val anchor = wp.getAsJsonObject("anchor")
            val x = anchor.get("x").asDouble
            val y = anchor.get("y").asDouble
            
            // Simplified heading: 0 degrees for all waypoints, or derive from path direction
            // In a real implementation, we'd parse rotation targets.
            var heading = 0.0
            
            // Distance calculation
            if (i > 0) {
                val prev = pathPoints.last().pose
                val dx = x - prev.x
                val dy = y - prev.y
                accumulatedDistance += hypot(dx, dy)
                // Approximate heading as the direction of travel
                heading = kotlin.math.atan2(dy, dx)
            }

            pathPoints.add(
                PathPoint(
                    pose = Pose2d(x, y, Rotation2d(heading)),
                    velocityMps = 1.0, // Default velocity
                    distanceMeters = accumulatedDistance
                )
            )
        }

        return Path(pathPoints)
    }
}
