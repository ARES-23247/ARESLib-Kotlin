package com.areslib.pathing.planner

import com.areslib.pathing.Costmap

/**
 * Object implementation for Grid Costmap Inflator.
 *
 * Autonomous path planning, trajectory generation, and obstacle avoidance module.
 *
 * ### Coordinate System:
 * Field-centric coordinates in meters ($m$) relative to field origin.
 */
object GridCostmapInflator {
    /**
     * inflate declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun inflate(
        grid: BooleanArray,
        inflatedGrid: BooleanArray,
        widthCells: Int,
        heightCells: Int,
        robotRadiusMeters: Double,
        resolutionMeters: Double
    ) {
        inflatedGrid.fill(false)
        val cellRadius = (robotRadiusMeters / resolutionMeters).toInt().coerceAtLeast(1)

        for (cy in 0 until heightCells) {
            for (cx in 0 until widthCells) {
                if (grid[cy * widthCells + cx]) {
                    // Inflate outward in a circular radius
                    for (dy in -cellRadius..cellRadius) {
                        for (dx in -cellRadius..cellRadius) {
                            if (dx * dx + dy * dy <= cellRadius * cellRadius) {
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in 0 until widthCells && ny in 0 until heightCells) {
                                    inflatedGrid[ny * widthCells + nx] = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * insertDynamicObstacle declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun insertDynamicObstacle(
        inflatedGrid: BooleanArray,
        widthCells: Int,
        heightCells: Int,
        cellX: Int,
        cellY: Int,
        radiusMeters: Double,
        resolutionMeters: Double
    ) {
        val cellRadius = (radiusMeters / resolutionMeters).toInt().coerceAtLeast(1)

        for (dy in -cellRadius..cellRadius) {
            for (dx in -cellRadius..cellRadius) {
                if (dx * dx + dy * dy <= cellRadius * cellRadius) {
                    val nx = cellX + dx
                    val ny = cellY + dy
                    if (nx in 0 until widthCells && ny in 0 until heightCells) {
                        inflatedGrid[ny * widthCells + nx] = true
                    }
                }
            }
        }
    }
}
