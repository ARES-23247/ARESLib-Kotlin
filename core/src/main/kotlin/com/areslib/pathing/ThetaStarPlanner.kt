package com.areslib.pathing

import com.areslib.math.Translation2d
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

    private class LongHeap(capacity: Int) {
        var data = LongArray(capacity)
        var size = 0

        fun add(value: Long) {
            if (size == data.size) {
                data = data.copyOf(data.size * 2)
            }
            var i = size
            size++
            while (i > 0) {
                val p = (i - 1) ushr 1
                if (data[p] <= value) break
                data[i] = data[p]
                i = p
            }
            data[i] = value
        }

        fun poll(): Long {
            val result = data[0]
            size--
            if (size > 0) {
                val value = data[size]
                var i = 0
                while ((i shl 1) + 1 < size) {
                    var child = (i shl 1) + 1
                    if (child + 1 < size && data[child + 1] < data[child]) {
                        child++
                    }
                    if (value <= data[child]) break
                    data[i] = data[child]
                    i = child
                }
                data[i] = value
            }
            return result
        }

        fun clear() { size = 0 }
        
        fun isNotEmpty(): Boolean = size > 0
    }

    private class PlannerState(capacity: Int) {
        var gCosts = DoubleArray(capacity) { Double.POSITIVE_INFINITY }
        var parents = IntArray(capacity) { -1 }
        var closedSet = BooleanArray(capacity)
        var openQueue = LongHeap(capacity)
        var pathPool = Array(capacity) { Translation2d() }

        fun ensureCapacity(capacity: Int) {
            if (gCosts.size < capacity) {
                gCosts = DoubleArray(capacity) { Double.POSITIVE_INFINITY }
                parents = IntArray(capacity) { -1 }
                closedSet = BooleanArray(capacity)
            } else {
                gCosts.fill(Double.POSITIVE_INFINITY, 0, capacity)
                parents.fill(-1, 0, capacity)
                closedSet.fill(false, 0, capacity)
            }
            openQueue.clear()
            if (pathPool.size < capacity) {
                val newPool = Array(capacity) { Translation2d() }
                System.arraycopy(pathPool, 0, newPool, 0, pathPool.size)
                pathPool = newPool
            }
        }
    }

    private val threadLocalState = ThreadLocal.withInitial { PlannerState(10000) }

    /**
     * Plans a globally safe path from start to end coordinates.
     * @return List of field-relative Translation2d coordinates.
     */
    fun plan(
        costmap: Costmap,
        start: Translation2d,
        end: Translation2d
    ): List<Translation2d> {
        if (costmap.resolutionMeters <= 0.0 || costmap.resolutionMeters.isInfinite() ||
            !start.x.isFinite() || !start.y.isFinite() || !end.x.isFinite() || !end.y.isFinite()) {
            System.err.println("ThetaStarPlanner: Invalid planning parameters (resolution=${costmap.resolutionMeters}, start=$start, end=$end). Returning empty path.")
            return emptyList()
        }

        val startX = ((start.x - costmap.origin.x) / costmap.resolutionMeters).roundToInt()
        val startY = ((start.y - costmap.origin.y) / costmap.resolutionMeters).roundToInt()
        val endX = ((end.x - costmap.origin.x) / costmap.resolutionMeters).roundToInt()
        val endY = ((end.y - costmap.origin.y) / costmap.resolutionMeters).roundToInt()

        // Handle simple start == end edge case
        if (startX == endX && startY == endY) {
            return listOf(start, end)
        }

        // Out of bounds check
        if (startX !in 0 until costmap.widthCells || startY !in 0 until costmap.heightCells) return emptyList()
        if (endX !in 0 until costmap.widthCells || endY !in 0 until costmap.heightCells) return emptyList()

        val capacity = costmap.widthCells * costmap.heightCells
        val state = threadLocalState.get()
        state.ensureCapacity(capacity)

        val gCosts = state.gCosts
        val parents = state.parents
        val closedSet = state.closedSet
        val openQueue = state.openQueue

        val startKey = startY * costmap.widthCells + startX
        val endKey = endY * costmap.widthCells + endX

        gCosts[startKey] = 0.0
        parents[startKey] = startKey
        
        val startH = heuristic(startX, startY, endX, endY)
        val startFloatBits = startH.toFloat().toBits().toLong() and 0xFFFFFFFFL
        val startHeapVal = (startFloatBits shl 32) or (startKey.toLong() and 0xFFFFFFFFL)
        openQueue.add(startHeapVal)

        while (openQueue.isNotEmpty()) {
            val heapVal = openQueue.poll()
            val currKey = (heapVal and 0xFFFFFFFFL).toInt()

            if (closedSet[currKey]) continue

            val currX = currKey % costmap.widthCells
            val currY = currKey / costmap.widthCells

            if (currX == endX && currY == endY) {
                // Path found! Reconstruct waypoints
                return reconstructPath(currKey, costmap, start, end, parents)
            }

            closedSet[currKey] = true

            // Explore 8-way neighbors
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue

                    val nx = currX + dx
                    val ny = currY + dy

                    // Ensure cell is bounds and traversable
                    if (!costmap.isCellTraversable(nx, ny)) continue

                    val nKey = ny * costmap.widthCells + nx
                    if (closedSet[nKey]) continue

                    updateVertex(currKey, currX, currY, nKey, nx, ny, costmap, endX, endY, state)
                }
            }
        }

        return emptyList() // Return empty if no path found
    }

    private fun updateVertex(
        currKey: Int,
        currX: Int,
        currY: Int,
        nKey: Int,
        nx: Int,
        ny: Int,
        costmap: Costmap,
        endX: Int,
        endY: Int,
        state: PlannerState
    ) {
        val gCosts = state.gCosts
        val parents = state.parents
        val openQueue = state.openQueue

        var parentKey = parents[currKey]
        if (parentKey == -1) parentKey = currKey

        val parentX = parentKey % costmap.widthCells
        val parentY = parentKey / costmap.widthCells

        // Theta* Line of Sight Check:
        // If there is line of sight from the parent to the neighbor, 
        // bypass the current node to allow any-angle straight pathing.
        if (lineOfSight(costmap, parentX, parentY, nx, ny)) {
            val newG = gCosts[parentKey] + distance(parentX, parentY, nx, ny)
            if (newG < gCosts[nKey]) {
                gCosts[nKey] = newG
                parents[nKey] = parentKey
                
                val f = newG + heuristic(nx, ny, endX, endY)
                val floatBits = f.toFloat().toBits().toLong() and 0xFFFFFFFFL
                val heapVal = (floatBits shl 32) or (nKey.toLong() and 0xFFFFFFFFL)
                openQueue.add(heapVal)
            }
        } else {
            // Fall back to standard A* update
            val newG = gCosts[currKey] + distance(currX, currY, nx, ny)
            if (newG < gCosts[nKey]) {
                gCosts[nKey] = newG
                parents[nKey] = currKey
                
                val f = newG + heuristic(nx, ny, endX, endY)
                val floatBits = f.toFloat().toBits().toLong() and 0xFFFFFFFFL
                val heapVal = (floatBits shl 32) or (nKey.toLong() and 0xFFFFFFFFL)
                openQueue.add(heapVal)
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
        endKey: Int,
        costmap: Costmap,
        start: Translation2d,
        end: Translation2d,
        parents: IntArray
    ): List<Translation2d> {
        val state = threadLocalState.get()
        var currKey = endKey

        var iterations = 0
        val maxIterations = costmap.widthCells * costmap.heightCells
        var pathSize = 0

        while (iterations < maxIterations) {
            iterations++
            val currX = currKey % costmap.widthCells
            val currY = currKey / costmap.widthCells

            val fx = currX * costmap.resolutionMeters + costmap.origin.x
            val fy = currY * costmap.resolutionMeters + costmap.origin.y
            
            if (pathSize >= state.pathPool.size) {
                val newPool = Array(state.pathPool.size * 2) { Translation2d() }
                System.arraycopy(state.pathPool, 0, newPool, 0, state.pathPool.size)
                state.pathPool = newPool
            }
            state.pathPool[pathSize].x = fx
            state.pathPool[pathSize].y = fy
            pathSize++

            val parentKey = parents[currKey]
            if (parentKey == currKey || parentKey == -1) break
            currKey = parentKey
        }
        if (iterations >= maxIterations) {
            System.err.println("ThetaStarPlanner: Cyclic parents detected during path reconstruction! Breaking loop safely.")
        }

        // Reverse path in place
        for (i in 0 until pathSize / 2) {
            val opp = pathSize - 1 - i
            val tempX = state.pathPool[i].x
            val tempY = state.pathPool[i].y
            state.pathPool[i].x = state.pathPool[opp].x
            state.pathPool[i].y = state.pathPool[opp].y
            state.pathPool[opp].x = tempX
            state.pathPool[opp].y = tempY
        }

        // Snap start and end points perfectly to their true exact physical starting coordinates
        if (pathSize > 0) {
            state.pathPool[0].x = start.x
            state.pathPool[0].y = start.y
            state.pathPool[pathSize - 1].x = end.x
            state.pathPool[pathSize - 1].y = end.y
        }

        return state.pathPool.asList().subList(0, pathSize)
    }
}
