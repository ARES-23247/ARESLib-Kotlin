package com.areslib.pathing.planner

import com.areslib.pathing.Costmap

/**
 * Object implementation for Line Of Sight Checker.
 *
 * Autonomous path planning, trajectory generation, and obstacle avoidance module.
 *
 * ### Coordinate System:
 * Field-centric coordinates in meters ($m$) relative to field origin.
 */
object LineOfSightChecker {
    /**
     * High-speed Bresenham line algorithm for line-of-sight collision checking.
     */
    fun lineOfSight(costmap: Costmap, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        var cx = x0
        var cy = y0

        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            if (!costmap.isCellTraversable(cx, cy)) {
                return false
            }

            if (cx == x1 && cy == y1) break

            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx += sx
            }
            if (e2 < dx) {
                err += dx
                cy += sy
            }
        }

        return true
    }
}
