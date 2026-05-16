package com.areslib.auto

import com.areslib.math.Translation2d
import com.areslib.math.Rotation2d

/**
 * Pure JSON Parser for PathPlanner trajectories.
 * Takes a pure String, throws away the hardware-specific file I/O.
 */
object PathParser {
    fun fromJson(jsonString: String): Trajectory {
        // Stub: A real implementation would parse the JSON string into TrajectoryStates
        // without depending on Jackson/Moshi to keep the core dependency-free.
        return Trajectory(
            listOf(
                TrajectoryState(
                    timeSeconds = 0.0,
                    velocityMetersPerSecond = 0.0,
                    poseMeters = Translation2d(0.0, 0.0),
                    headingRadians = Rotation2d()
                )
            )
        )
    }
}
