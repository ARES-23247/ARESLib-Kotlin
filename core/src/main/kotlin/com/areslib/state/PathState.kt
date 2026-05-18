package com.areslib.state

import com.areslib.pathing.Path

/**
 * Holds state for active path tracking, chaining, and detour events.
 */
data class PathState(
    val activePath: Path? = null,
    val currentDistanceMeters: Double = 0.0,
    val isChained: Boolean = false,
    val detourActive: Boolean = false,
    val originalPathBeforeDetour: Path? = null
)
