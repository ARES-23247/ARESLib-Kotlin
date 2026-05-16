package com.areslib.auto

import com.areslib.math.Translation2d
import com.areslib.math.Rotation2d

data class TrajectoryState(
    val timeSeconds: Double,
    val velocityMetersPerSecond: Double,
    val poseMeters: Translation2d,
    val headingRadians: Rotation2d
)

data class Trajectory(
    val states: List<TrajectoryState>
) {
    fun sample(timeSeconds: Double): TrajectoryState {
        // Mock sample: return the first state or interpolate
        return states.firstOrNull() ?: TrajectoryState(0.0, 0.0, Translation2d(), Rotation2d())
    }
}
