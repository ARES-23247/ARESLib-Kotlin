package com.areslib.pathing

import com.areslib.math.Translation2d
import kotlin.math.roundToInt

/**
 * High-performance 2D occupancy grid costmap.
 * Supports static obstacle mapping, inflation radii, and dynamic dynamic inserts.
 */
class Costmap(
    val widthMeters: Double = 16.0,
    val heightMeters: Double = 8.0,
    val resolutionMeters: Double = 0.1,
    val origin: Translation2d = Translation2d(-widthMeters / 2.0, -heightMeters / 2.0)
) {
    val widthCells: Int = (widthMeters / resolutionMeters).toInt()
    val heightCells: Int = (heightMeters / resolutionMeters).toInt()

    // 1D backed array for cash-locality and zero-allocation updates
    private val grid = BooleanArray(widthCells * heightCells)
    private val inflatedGrid = BooleanArray(widthCells * heightCells)

    /**
     * Resets the costmap grid state.
     */
    fun clear() {
        grid.fill(false)
        inflatedGrid.fill(false)
    }

    /**
     * Marks a cell coordinate as occupied.
     */
    fun setObstacle(cellX: Int, cellY: Int, isOccupied: Boolean = true) {
        if (cellX in 0 until widthCells && cellY in 0 until heightCells) {
            grid[cellY * widthCells + cellX] = isOccupied
        }
    }

    /**
     * Checks if a field-relative coordinate is traversable.
     */
    fun isTraversable(x: Double, y: Double): Boolean {
        val cellX = ((x - origin.x) / resolutionMeters).roundToInt()
        val cellY = ((y - origin.y) / resolutionMeters).roundToInt()
        return isCellTraversable(cellX, cellY)
    }

    /**
     * Checks if a grid cell coordinate is traversable.
     */
    fun isCellTraversable(cellX: Int, cellY: Int): Boolean {
        if (cellX !in 0 until widthCells || cellY !in 0 until heightCells) {
            return false // Out-of-bounds is non-traversable
        }
        return !inflatedGrid[cellY * widthCells + cellX]
    }

    /**
     * Sets a field-relative coordinate as an obstacle.
     */
    fun setObstacle(x: Double, y: Double, isOccupied: Boolean = true) {
        val cellX = ((x - origin.x) / resolutionMeters).roundToInt()
        val cellY = ((y - origin.y) / resolutionMeters).roundToInt()
        setObstacle(cellX, cellY, isOccupied)
    }

    /**
     * Inflates the obstacle boundaries by the robot's physical bumper radius.
     * Prevents any paths from running the chassis edges directly into structures.
     * @param robotRadiusMeters Radius of the robot bumper boundary.
     */
    fun inflate(robotRadiusMeters: Double) {
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
     * Dynamic insert of a dynamic obstacle (e.g. an opponent robot).
     */
    fun insertDynamicObstacle(x: Double, y: Double, radiusMeters: Double) {
        val cellX = ((x - origin.x) / resolutionMeters).roundToInt()
        val cellY = ((y - origin.y) / resolutionMeters).roundToInt()
        val cellRadius = (radiusMeters / resolutionMeters).roundToInt().coerceAtLeast(1)

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
