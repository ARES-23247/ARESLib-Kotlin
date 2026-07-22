package com.areslib.pathing.planner

/**
 * Class implementation for Long Heap.
 *
 * Autonomous path planning, trajectory generation, and obstacle avoidance module.
 *
 * ### Coordinate System:
 * Field-centric coordinates in meters ($m$) relative to field origin.
 */
class LongHeap(capacity: Int) {
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

/**
 * Class implementation for Planner State.
 *
 * Autonomous path planning, trajectory generation, and obstacle avoidance module.
 *
 * ### Coordinate System:
 * Field-centric coordinates in meters ($m$) relative to field origin.
 */
class PlannerState(capacity: Int) {
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
