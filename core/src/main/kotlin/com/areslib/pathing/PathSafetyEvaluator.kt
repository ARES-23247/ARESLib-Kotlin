package com.areslib.pathing

import com.areslib.math.Translation2d
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Details safety metrics computed for a given robot navigation path.
 */
data class SafetyReport(
    val isSafe: Boolean,
    val minimumDistanceToObstacleMeters: Double,
    val averageObstacleDensity: Double,
    val maxObstacleDensity: Double,
    val recommendedSpeedMultiplier: Double
)

/**
 * High-performance, allocation-minimized global path safety evaluator.
 * Analyzes planned paths against raw grid obstacle costmaps to detect tight corridors,
 * compute obstacle densities, and recommend proactive velocity scaling factors.
 */
object PathSafetyEvaluator {

    private var occupiedX = DoubleArray(1024)
    private var occupiedY = DoubleArray(1024)

    private fun ensureCapacity(size: Int) {
        if (occupiedX.size < size) {
            occupiedX = DoubleArray(size * 2)
            occupiedY = DoubleArray(size * 2)
        }
    }

    private class EvaluationState {
        var totalPoints = 0
        var minDistanceToObstacle = Double.MAX_VALUE
        var sumObstacleDensity = 0.0
        var peakObstacleDensity = 0.0

        fun reset() {
            totalPoints = 0
            minDistanceToObstacle = Double.MAX_VALUE
            sumObstacleDensity = 0.0
            peakObstacleDensity = 0.0
        }
    }

    private val evalState = EvaluationState()

    private fun evaluatePoint(
        px: Double,
        py: Double,
        occupiedX: DoubleArray,
        occupiedY: DoubleArray,
        occupiedCount: Int,
        searchRadiusMeters: Double,
        costmap: Costmap,
        state: EvaluationState
    ) {
        state.totalPoints++

        // 1. Calculate minimum distance to any raw occupied cell
        var pointMinDist = Double.MAX_VALUE
        for (i in 0 until occupiedCount) {
            val cellX = occupiedX[i]
            val cellY = occupiedY[i]
            val dist = hypot(px - cellX, py - cellY)
            if (dist < pointMinDist) {
                pointMinDist = dist
            }
        }
        if (pointMinDist < state.minDistanceToObstacle) {
            state.minDistanceToObstacle = pointMinDist
        }

        // 2. Calculate local obstacle density within searchRadiusMeters
        var totalCells = 0
        var occupiedCountLocal = 0
        val minCellX = (((px - searchRadiusMeters) - costmap.origin.x) / costmap.resolutionMeters).roundToInt().coerceAtLeast(0)
        val maxCellX = (((px + searchRadiusMeters) - costmap.origin.x) / costmap.resolutionMeters).roundToInt().coerceAtMost(costmap.widthCells - 1)
        val minCellY = (((py - searchRadiusMeters) - costmap.origin.y) / costmap.resolutionMeters).roundToInt().coerceAtLeast(0)
        val maxCellY = (((py + searchRadiusMeters) - costmap.origin.y) / costmap.resolutionMeters).roundToInt().coerceAtMost(costmap.heightCells - 1)

        for (cy in minCellY..maxCellY) {
            for (cx in minCellX..maxCellX) {
                val cellCenterX = costmap.origin.x + cx * costmap.resolutionMeters
                val cellCenterY = costmap.origin.y + cy * costmap.resolutionMeters
                val dist = hypot(px - cellCenterX, py - cellCenterY)
                if (dist <= searchRadiusMeters) {
                    totalCells++
                    if (costmap.isCellOccupied(cx, cy)) {
                        occupiedCountLocal++
                    }
                }
            }
        }

        val density = if (totalCells > 0) occupiedCountLocal.toDouble() / totalCells else 0.0
        state.sumObstacleDensity += density
        if (density > state.peakObstacleDensity) {
            state.peakObstacleDensity = density
        }
    }

    /**
     * Evaluates the safety of a given path on a costmap.
     *
     * @param path The planned path as a list of 2D translation coordinates.
     * @param costmap The raw grid occupancy costmap.
     * @param searchRadiusMeters The radius in meters to analyze local obstacle density around each path point.
     * @param robotRadiusMeters The physical bumper radius of the robot in meters.
     * @return A safety report detailing distances, densities, and speed suggestions.
     */
    @Synchronized
    fun evaluate(
        path: List<Translation2d>,
        costmap: Costmap,
        searchRadiusMeters: Double = 0.5,
        robotRadiusMeters: Double = 0.25
    ): SafetyReport {
        if (path.isEmpty()) {
            return SafetyReport(
                isSafe = false,
                minimumDistanceToObstacleMeters = 0.0,
                averageObstacleDensity = 0.0,
                maxObstacleDensity = 0.0,
                recommendedSpeedMultiplier = 0.0
            )
        }

        val maxCells = costmap.heightCells * costmap.widthCells
        ensureCapacity(maxCells)
        var occupiedCount = 0
        for (cy in 0 until costmap.heightCells) {
            for (cx in 0 until costmap.widthCells) {
                if (costmap.isCellOccupied(cx, cy)) {
                    occupiedX[occupiedCount] = costmap.origin.x + cx * costmap.resolutionMeters
                    occupiedY[occupiedCount] = costmap.origin.y + cy * costmap.resolutionMeters
                    occupiedCount++
                }
            }
        }

        evalState.reset()

        val rawStepSize = costmap.resolutionMeters * 0.5
        val isInvalidStep = rawStepSize.isNaN() || rawStepSize.isInfinite() || rawStepSize <= 1e-4
        val stepSize = if (isInvalidStep) 0.05 else rawStepSize

        // Interpolate along the continuous segments of the path
        if (path.size == 1) {
            evaluatePoint(path[0].x, path[0].y, occupiedX, occupiedY, occupiedCount, searchRadiusMeters, costmap, evalState)
        } else {
            for (i in 0 until path.size - 1) {
                val p1 = path[i]
                val p2 = path[i + 1]
                val segmentDist = hypot(p2.x - p1.x, p2.y - p1.y)

                evaluatePoint(p1.x, p1.y, occupiedX, occupiedY, occupiedCount, searchRadiusMeters, costmap, evalState)

                if (segmentDist > 0.0) {
                    var step = stepSize
                    while (step < segmentDist) {
                        val t = step / segmentDist
                        val px = p1.x + t * (p2.x - p1.x)
                        val py = p1.y + t * (p2.y - p1.y)
                        evaluatePoint(px, py, occupiedX, occupiedY, occupiedCount, searchRadiusMeters, costmap, evalState)
                        step += stepSize
                    }
                }
            }
            evaluatePoint(path.last().x, path.last().y, occupiedX, occupiedY, occupiedCount, searchRadiusMeters, costmap, evalState)
        }

        if (occupiedCount == 0) {
            evalState.minDistanceToObstacle = 10.0 // Set to a large default if field is completely clear
        }

        val avgObstacleDensity = if (evalState.totalPoints > 0) evalState.sumObstacleDensity / evalState.totalPoints else 0.0

        // Determine safety status
        // A path is safe if minimum distance is at least robotRadius + 5cm clearance
        val isSafe = evalState.minDistanceToObstacle >= (robotRadiusMeters + 0.05)

        val recommendedSpeedMultiplier = when {
            evalState.minDistanceToObstacle < (robotRadiusMeters + 0.05) -> 0.3
            evalState.minDistanceToObstacle < (robotRadiusMeters + 0.15) || evalState.peakObstacleDensity > 0.5 -> 0.3
            evalState.minDistanceToObstacle < (robotRadiusMeters + 0.35) || evalState.peakObstacleDensity > 0.2 -> 0.6
            else -> 1.0
        }

        return SafetyReport(
            isSafe = isSafe,
            minimumDistanceToObstacleMeters = evalState.minDistanceToObstacle,
            averageObstacleDensity = avgObstacleDensity,
            maxObstacleDensity = evalState.peakObstacleDensity,
            recommendedSpeedMultiplier = recommendedSpeedMultiplier
        )
    }
}
