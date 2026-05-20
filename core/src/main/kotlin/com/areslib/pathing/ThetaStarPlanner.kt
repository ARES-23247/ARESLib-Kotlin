package com.areslib.pathing

import com.areslib.math.Translation2d
import java.util.PriorityQueue
import kotlin.math.roundToInt

/**
 * A state-of-the-art any-angle global pathfinder implementing the **Theta\*** path planning algorithm.
 *
 * Traditional A* restricts paths to grid lines, generating jagged, artificial zigzag patterns.
 * Theta* bypasses these restrictions by performing a high-speed Bresenham line-of-sight check during
 * neighbor expansion. If a direct line-of-sight exists between a candidate node's parent and a neighbor,
 * the path skips the candidate, linking the neighbor directly to the parent.
 *
 * This results in mathematically optimal, straight, grid-snap-free global paths around costmap obstacles.
 */
object ThetaStarPlanner {

    private class Node(
        val x: Int,
        val y: Int,
        var g: Double = Double.POSITIVE_INFINITY,
        var h: Double = 0.0,
        var parent: Node? = null
    ) : Comparable<Node> {
        val f: Double get() = g + h

        override fun compareTo(other: Node): Int {
            return f.compareTo(other.f)
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Node) return false
            return x == other.x && y == other.y
        }

        override fun hashCode(): Int {
            return x * 31 + y
        }
    }

    /**
     * Plans a globally safe path from start to end coordinates.
     * @return List of field-relative Translation2d coordinates.
     */
    fun plan(
        costmap: Costmap,
        start: Translation2d,
        end: Translation2d
    ): List<Translation2d> {
        val startX = ((start.x - costmap.origin.x) / costmap.resolutionMeters).roundToInt()
        val startY = ((start.y - costmap.origin.y) / costmap.resolutionMeters).roundToInt()
        val endX = ((end.x - costmap.origin.x) / costmap.resolutionMeters).roundToInt()
        val endY = ((end.y - costmap.origin.y) / costmap.resolutionMeters).roundToInt()

        // Handle simple start == end edge case
        if (startX == endX && startY == endY) {
            return listOf(start, end)
        }

        // Closed set and Node Cache to prevent object re-allocations
        val openSet = PriorityQueue<Node>()
        val closedSet = HashSet<Int>()
        val nodeMap = HashMap<Int, Node>()

        fun getNode(x: Int, y: Int): Node {
            val key = y * costmap.widthCells + x
            return nodeMap.getOrPut(key) { Node(x, y) }
        }

        val startNode = getNode(startX, startY)
        startNode.g = 0.0
        startNode.h = heuristic(startX, startY, endX, endY)
        startNode.parent = startNode
        openSet.add(startNode)

        while (openSet.isNotEmpty()) {
            val curr = openSet.poll()

            if (curr.x == endX && curr.y == endY) {
                // Path found! Reconstruct waypoints
                return reconstructPath(curr, costmap, start, end)
            }

            val key = curr.y * costmap.widthCells + curr.x
            closedSet.add(key)

            // Explore 8-way neighbors
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue

                    val nx = curr.x + dx
                    val ny = curr.y + dy

                    // Ensure cell is bounds and traversable
                    if (!costmap.isCellTraversable(nx, ny)) continue

                    val nKey = ny * costmap.widthCells + nx
                    if (closedSet.contains(nKey)) continue

                    val neighbor = getNode(nx, ny)
                    updateVertex(curr, neighbor, openSet, costmap, endX, endY)
                }
            }
        }

        return emptyList() // Return empty if no path found
    }

    private fun updateVertex(
        curr: Node,
        neighbor: Node,
        openSet: PriorityQueue<Node>,
        costmap: Costmap,
        endX: Int,
        endY: Int
    ) {
        val parent = curr.parent ?: curr

        // Theta* Line of Sight Check:
        // If there is line of sight from the parent to the neighbor, 
        // bypass the current node to allow any-angle straight pathing.
        if (lineOfSight(costmap, parent.x, parent.y, neighbor.x, neighbor.y)) {
            val newG = parent.g + distance(parent.x, parent.y, neighbor.x, neighbor.y)
            if (newG < neighbor.g) {
                openSet.remove(neighbor)
                neighbor.g = newG
                neighbor.h = heuristic(neighbor.x, neighbor.y, endX, endY)
                neighbor.parent = parent
                openSet.add(neighbor)
            }
        } else {
            // Fall back to standard A* update
            val newG = curr.g + distance(curr.x, curr.y, neighbor.x, neighbor.y)
            if (newG < neighbor.g) {
                openSet.remove(neighbor)
                neighbor.g = newG
                neighbor.h = heuristic(neighbor.x, neighbor.y, endX, endY)
                neighbor.parent = curr
                openSet.add(neighbor)
            }
        }
    }

    /**
     * High-speed Bresenham line algorithm for line-of-sight collision checking.
     */
    private fun lineOfSight(costmap: Costmap, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
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

    private fun distance(x0: Int, y0: Int, x1: Int, y1: Int): Double {
        val dx = (x1 - x0).toDouble()
        val dy = (y1 - y0).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun heuristic(x0: Int, y0: Int, x1: Int, y1: Int): Double {
        // Octile distance heuristic for 8-way diagonal path planning
        val dx = kotlin.math.abs(x1 - x0).toDouble()
        val dy = kotlin.math.abs(y1 - y0).toDouble()
        val min = kotlin.math.min(dx, dy)
        val max = kotlin.math.max(dx, dy)
        return (max - min) + min * 1.414
    }

    private fun reconstructPath(
        endNode: Node,
        costmap: Costmap,
        start: Translation2d,
        end: Translation2d
    ): List<Translation2d> {
        val path = mutableListOf<Translation2d>()
        var curr: Node? = endNode

        while (curr != null) {
            val fx = curr.x * costmap.resolutionMeters + costmap.origin.x
            val fy = curr.y * costmap.resolutionMeters + costmap.origin.y
            path.add(Translation2d(fx, fy))

            if (curr.parent == curr) break
            curr = curr.parent
        }

        path.reverse()

        // Snap start and end points perfectly to their true exact physical starting coordinates
        if (path.isNotEmpty()) {
            path[0] = start
            path[path.size - 1] = end
        }

        return path
    }
}
