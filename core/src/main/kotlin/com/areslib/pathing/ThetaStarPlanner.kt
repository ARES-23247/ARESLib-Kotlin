package com.areslib.pathing

import com.areslib.math.geometry.Translation2d
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
        var gCosts = DoubleArray(capacity)
        var parents = IntArray(capacity)
        var generations = IntArray(capacity)
        var generation = 0
        var openQueue = LongHeap(capacity)
        var pathPool = DoubleArray(capacity * 2)

        fun ensureCapacity(capacity: Int) {
            if (gCosts.size < capacity) {
                gCosts = DoubleArray(capacity)
                parents = IntArray(capacity)
                generations = IntArray(capacity)
            }
            // Advance epoch — all nodes with stale generation are implicitly reset
            generation++
            if (generation == Int.MAX_VALUE) {
                // Overflow guard: reset epoch and zero out generations array
                generation = 1
                generations.fill(0, 0, generations.size)
            }
            openQueue.clear()
            if (pathPool.size < capacity * 2) {
                val newPool = DoubleArray(capacity * 2)
                System.arraycopy(pathPool, 0, newPool, 0, pathPool.size)
                pathPool = newPool
            }
        }

        /** Read gCost for a node, returning POSITIVE_INFINITY if the node hasn't been touched this epoch. */
        fun getGCost(key: Int): Double {
            return if (generations[key] == generation) gCosts[key] else Double.POSITIVE_INFINITY
        }

        /** Write gCost for a node, marking it as active in the current epoch. */
        fun setGCost(key: Int, value: Double) {
            gCosts[key] = value
            generations[key] = generation
        }

        /** Check if a node has been closed (visited) this epoch. Uses the sign bit of parents as a flag. */
        fun isClosed(key: Int): Boolean {
            return generations[key] == generation && parents[key] < -1
        }

        /** Mark a node as closed by encoding it into the parent value (negate and subtract 2). */
        fun setClosed(key: Int) {
            // Encode: closedParent = -(realParent) - 2, so realParent >= -1 maps to closedParent <= -2
            parents[key] = -(parents[key]) - 2
        }

        /** Get the real parent key, whether the node is closed or open. */
        fun getParent(key: Int): Int {
            if (generations[key] != generation) return -1
            val p = parents[key]
            return if (p < -1) -(p + 2) else p
        }

        /** Set the parent for a node (open state). */
        fun setParent(key: Int, parentKey: Int) {
            parents[key] = parentKey
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
        if (!costmap.isCellTraversable(endX, endY)) return emptyList()

        val capacity = costmap.widthCells * costmap.heightCells
        val state = threadLocalState.get()
        state.ensureCapacity(capacity)

        val openQueue = state.openQueue

        val startKey = startY * costmap.widthCells + startX

        state.setGCost(startKey, 0.0)
        state.setParent(startKey, startKey)
        
        val startH = heuristic(startX, startY, endX, endY)
        val startFloatBits = startH.toFloat().toBits().toLong() and 0xFFFFFFFFL
        val startHeapVal = (startFloatBits shl 32) or (startKey.toLong() and 0xFFFFFFFFL)
        openQueue.add(startHeapVal)

        while (openQueue.isNotEmpty()) {
            val heapVal = openQueue.poll()
            val currKey = (heapVal and 0xFFFFFFFFL).toInt()

            if (state.isClosed(currKey)) continue

            val currX = currKey % costmap.widthCells
            val currY = currKey / costmap.widthCells

            if (currX == endX && currY == endY) {
                // Path found! Reconstruct waypoints
                return reconstructPath(currKey, costmap, start, end, state)
            }

            state.setClosed(currKey)

            // Explore 8-way neighbors
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue

                    val nx = currX + dx
                    val ny = currY + dy

                    // Ensure cell is bounds and traversable
                    if (!costmap.isCellTraversable(nx, ny)) continue

                    val nKey = ny * costmap.widthCells + nx
                    if (state.isClosed(nKey)) continue

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
        val openQueue = state.openQueue

        var parentKey = state.getParent(currKey)
        if (parentKey == -1) parentKey = currKey

        val parentX = parentKey % costmap.widthCells
        val parentY = parentKey / costmap.widthCells

        // Theta* Line of Sight Check:
        // If there is line of sight from the parent to the neighbor, 
        // bypass the current node to allow any-angle straight pathing.
        if (lineOfSight(costmap, parentX, parentY, nx, ny)) {
            val newG = state.getGCost(parentKey) + distance(parentX, parentY, nx, ny)
            if (newG < state.getGCost(nKey)) {
                state.setGCost(nKey, newG)
                state.setParent(nKey, parentKey)
                
                val f = newG + heuristic(nx, ny, endX, endY)
                val floatBits = f.toFloat().toBits().toLong() and 0xFFFFFFFFL
                val heapVal = (floatBits shl 32) or (nKey.toLong() and 0xFFFFFFFFL)
                openQueue.add(heapVal)
            }
        } else {
            // Fall back to standard A* update
            val newG = state.getGCost(currKey) + distance(currX, currY, nx, ny)
            if (newG < state.getGCost(nKey)) {
                state.setGCost(nKey, newG)
                state.setParent(nKey, currKey)
                
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
        state: PlannerState
    ): List<Translation2d> {
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
            
            if (pathSize * 2 >= state.pathPool.size) {
                val newPool = DoubleArray(state.pathPool.size * 2)
                System.arraycopy(state.pathPool, 0, newPool, 0, state.pathPool.size)
                state.pathPool = newPool
            }
            state.pathPool[pathSize * 2] = fx
            state.pathPool[pathSize * 2 + 1] = fy
            pathSize++

            val parentKey = state.getParent(currKey)
            if (parentKey == currKey || parentKey == -1) break
            currKey = parentKey
        }
        if (iterations >= maxIterations) {
            System.err.println("ThetaStarPlanner: Cyclic parents detected during path reconstruction! Breaking loop safely.")
        }

        // Reverse path in place
        for (i in 0 until pathSize / 2) {
            val opp = pathSize - 1 - i
            val tempX = state.pathPool[i * 2]
            val tempY = state.pathPool[i * 2 + 1]
            state.pathPool[i * 2] = state.pathPool[opp * 2]
            state.pathPool[i * 2 + 1] = state.pathPool[opp * 2 + 1]
            state.pathPool[opp * 2] = tempX
            state.pathPool[opp * 2 + 1] = tempY
        }

        // Snap start and end points perfectly to their true exact physical starting coordinates
        if (pathSize > 0) {
            state.pathPool[0] = start.x
            state.pathPool[1] = start.y
            state.pathPool[(pathSize - 1) * 2] = end.x
            state.pathPool[(pathSize - 1) * 2 + 1] = end.y
        }

        return List(pathSize) { i -> Translation2d(state.pathPool[i * 2], state.pathPool[i * 2 + 1]) }
    }
}
