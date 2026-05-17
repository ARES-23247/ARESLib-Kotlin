package com.areslib.pathing

/**
 * Represents an event marker placed along a PathPlanner trajectory.
 */
data class PathEvent(
    val eventName: String,
    val triggerDistanceMeters: Double
)
