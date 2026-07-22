package com.areslib.pathing

import com.areslib.math.geometry.Rotation2d
import com.areslib.math.geometry.Translation2d

/**
 * Facade object for parsing PathPlanner .path JSON files and dynamically generating smooth, profiled Paths.
 */
object PathPlannerParser {

    /**
     * Parses a PathPlanner .path JSON string into an immutable Path.
     * Generates a dense array of interpolations using Cubic Bezier math and applies Trapezoidal Motion Profiling.
     */
    fun parsePath(jsonString: String, maxVelocityMps: Double = 2.0, maxAccelerationMps2: Double = 1.5): Path {
        val parsedData = PathPlannerJsonParser.parse(jsonString, maxVelocityMps, maxAccelerationMps2)
        return SplineMotionProfiler.buildProfiledPath(parsedData)
    }

    /**
     * Dynamically generates a smooth, profiled Path from a list of coordinate points
     * using Hermite/Catmull-Rom spline control points and trapezoidal motion profiling.
     */
    fun generatePath(
        points: List<Translation2d>,
        startHeading: Rotation2d,
        endHeading: Rotation2d,
        maxVelocityMps: Double = 2.0,
        maxAccelerationMps2: Double = 1.5
    ): Path {
        return SplineMotionProfiler.generateHermitePath(
            points = points,
            startHeading = startHeading,
            endHeading = endHeading,
            maxVelocityMps = maxVelocityMps,
            maxAccelerationMps2 = maxAccelerationMps2
        )
    }
}
