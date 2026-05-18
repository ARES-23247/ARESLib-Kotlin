package com.areslib.pathing

import com.areslib.state.Obstacle
import com.areslib.math.Pose2d
import kotlin.math.*

class VFHPlanner(
    val numSectors: Int = 36,
    val sensingRangeMeters: Double = 2.0,
    val aConstant: Double = 2.0,
    val bConstant: Double = 0.5,
    val safetyThreshold: Double = 0.15,
    val wideValleySectors: Int = 6,
    val safetyMarginSectors: Int = 3
) {
    private val sectorWidthRad = 2.0 * PI / numSectors
    private var lastDetourSign = 0.0 // 1.0 = left (pos Y), -1.0 = right (neg Y), 0.0 = none

    /**
     * Compute a safe detour steering heading using Vector Field Histogram (VFH+).
     */
    fun computeDetourHeading(robotPose: Pose2d, targetHeadingRad: Double, obstacles: List<Obstacle>): Double {
        if (obstacles.isEmpty()) return targetHeadingRad

        val sectors = DoubleArray(numSectors)

        // Step 1: Accumulate obstacle density weights in sector bins
        for (obstacle in obstacles) {
            val dx = obstacle.x - robotPose.x
            val dy = obstacle.y - robotPose.y
            val distance = hypot(dx, dy)

            // Ignore obstacles outside sensing range
            if (distance > sensingRangeMeters || distance < 0.05) continue

            // Determine relative angle in [0, 2pi]
            var obstacleAngleRad = atan2(dy, dx)
            if (obstacleAngleRad < 0.0) {
                obstacleAngleRad += 2.0 * PI
            }

            val sectorIndex = (obstacleAngleRad / sectorWidthRad).toInt().coerceIn(0, numSectors - 1)
            val weight = max(0.0, (aConstant - bConstant * distance) / distance)
            sectors[sectorIndex] += weight
        }

        // Step 2: Smooth the histogram sector bins to account for robot physical width
        val smoothedSectors = DoubleArray(numSectors)
        for (i in 0 until numSectors) {
            val prevVal = sectors[(i - 1 + numSectors) % numSectors]
            val currVal = sectors[i]
            val nextVal = sectors[(i + 1) % numSectors]
            smoothedSectors[i] = (prevVal + 2.0 * currVal + nextVal) / 4.0
        }

        // Step 3: Find valleys (sectors below threshold)
        val valleys = mutableListOf<List<Int>>()
        var currentValley = mutableListOf<Int>()

        for (i in 0 until numSectors) {
            if (smoothedSectors[i] < safetyThreshold) {
                currentValley.add(i)
            } else {
                if (currentValley.isNotEmpty()) {
                    valleys.add(currentValley)
                    currentValley = mutableListOf()
                }
            }
        }
        if (currentValley.isNotEmpty()) {
            valleys.add(currentValley)
        }

        // Handle wrap-around valleys (sector 35 adjacent to sector 0)
        if (valleys.size > 1 && smoothedSectors[0] < safetyThreshold && smoothedSectors[numSectors - 1] < safetyThreshold) {
            val lastValley = valleys.last()
            val firstValley = valleys.first()
            if (lastValley.contains(numSectors - 1) && firstValley.contains(0)) {
                val mergedValley = lastValley + firstValley
                valleys.removeAt(valleys.size - 1)
                valleys[0] = mergedValley
            }
        }

        if (valleys.isEmpty()) {
            // No safe path found, fallback to target heading
            return targetHeadingRad
        }

        // Target angle mapped to [0, 2pi]
        var normalizedTargetRad = targetHeadingRad
        while (normalizedTargetRad < 0.0) normalizedTargetRad += 2.0 * PI
        while (normalizedTargetRad >= 2.0 * PI) normalizedTargetRad -= 2.0 * PI

        var bestHeading = targetHeadingRad
        var minHeadingDifference = Double.MAX_VALUE

        val ux = cos(targetHeadingRad)
        val uy = sin(targetHeadingRad)
        val robotProgress = robotPose.x * ux + robotPose.y * uy

        val hasUnpassedObstacles = obstacles.any { obs ->
            val obsProgress = obs.x * ux + obs.y * uy
            obsProgress + obs.radius + 0.15 > robotProgress
        }

        for (valley in valleys) {
            val candidates = mutableListOf<Double>()
            val targetSector = (normalizedTargetRad / sectorWidthRad).toInt().coerceIn(0, numSectors - 1)
            if (valley.contains(targetSector)) {
                candidates.add(normalizedTargetRad)
            }

            if (valley.size > wideValleySectors) {
                val firstSector = valley.first()
                val lastSector = valley.last()

                val angleFirst = firstSector * sectorWidthRad + (sectorWidthRad / 2.0)
                val angleLast = lastSector * sectorWidthRad + (sectorWidthRad / 2.0)

                candidates.add(angleFirst + safetyMarginSectors * sectorWidthRad)
                candidates.add(angleLast - safetyMarginSectors * sectorWidthRad)
            } else {
                var sumAngleX = 0.0
                var sumAngleY = 0.0
                for (idx in valley) {
                    val angle = idx * sectorWidthRad + (sectorWidthRad / 2.0)
                    sumAngleX += cos(angle)
                    sumAngleY += sin(angle)
                }
                candidates.add(atan2(sumAngleY, sumAngleX))
            }

            for (chosenHeading in candidates) {
                var diff = abs(chosenHeading - normalizedTargetRad)
                while (diff > PI) diff = 2.0 * PI - diff

                val currentDetourSign = sign(sin(chosenHeading - targetHeadingRad))
                var biasedDiff = diff
                if (hasUnpassedObstacles && lastDetourSign != 0.0 && currentDetourSign != lastDetourSign) {
                    biasedDiff += 10.0 // lock detour side until obstacle is passed
                } else if (lastDetourSign != 0.0 && currentDetourSign == lastDetourSign) {
                    biasedDiff -= 0.8
                }

                if (biasedDiff < minHeadingDifference) {
                    minHeadingDifference = biasedDiff
                    bestHeading = chosenHeading
                }
            }
        }

        val headingDiff = bestHeading - targetHeadingRad
        val nextDetourSign = if (abs(headingDiff) > 0.05) {
            sign(sin(headingDiff))
        } else {
            0.0
        }
        lastDetourSign = nextDetourSign

        return bestHeading
    }
}
