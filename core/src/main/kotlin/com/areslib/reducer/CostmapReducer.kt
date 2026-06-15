package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.CostmapState
import com.areslib.state.Obstacle
import com.areslib.math.Pose2d

object CostmapReducer {
    private val scratchObstacles = mutableListOf<Obstacle>()

    /**
     * Reduces the CostmapState slice based on obstacle distance scanning relative to robot coordinate frame.
     */
    fun reduce(state: CostmapState, action: RobotAction, robotPose: Pose2d): CostmapState {
        return when (action) {
            is RobotAction.ObstacleCostmapUpdate -> {
                val robotX = robotPose.x
                val robotY = robotPose.y
                val robotHeadingRad = robotPose.heading.radians

                val newList = synchronized(scratchObstacles) {
                    scratchObstacles.clear()
                    scratchObstacles.addAll(state.obstacles)

                    for (obs in action.observations) {
                        val sensorFieldAngle = robotHeadingRad + obs.angleOffsetRad
                        
                        val mountFieldX = obs.positionOffsetXMeters * kotlin.math.cos(robotHeadingRad) - 
                                          obs.positionOffsetYMeters * kotlin.math.sin(robotHeadingRad)
                        val mountFieldY = obs.positionOffsetXMeters * kotlin.math.sin(robotHeadingRad) + 
                                          obs.positionOffsetYMeters * kotlin.math.cos(robotHeadingRad)
                                          
                        val sensorFieldX = robotX + mountFieldX
                        val sensorFieldY = robotY + mountFieldY

                        if (obs.distanceMeters < obs.maxRangeMeters) {
                            val obstacleFieldX = sensorFieldX + obs.distanceMeters * kotlin.math.cos(sensorFieldAngle)
                            val obstacleFieldY = sensorFieldY + obs.distanceMeters * kotlin.math.sin(sensorFieldAngle)

                            val mergeThreshold = 0.3
                            var existingIdx = -1
                            for (i in 0 until scratchObstacles.size) {
                                val it = scratchObstacles[i]
                                if (kotlin.math.hypot(it.x - obstacleFieldX, it.y - obstacleFieldY) < mergeThreshold) {
                                    existingIdx = i
                                    break
                                }
                            }

                            val newObstacle = Obstacle(obstacleFieldX, obstacleFieldY)
                            if (existingIdx >= 0) {
                                scratchObstacles[existingIdx] = newObstacle
                            } else {
                                scratchObstacles.add(newObstacle)
                            }
                        } else {
                            for (i in scratchObstacles.size - 1 downTo 0) {
                                val obstacle = scratchObstacles[i]
                                val dx = obstacle.x - sensorFieldX
                                val dy = obstacle.y - sensorFieldY
                                val dist = kotlin.math.hypot(dx, dy)
                                val angleToObstacle = kotlin.math.atan2(dy, dx)
                                val angleDiff = com.areslib.math.InputMath.wrapAngle(angleToObstacle - sensorFieldAngle)
                                if (dist < obs.maxRangeMeters && kotlin.math.abs(angleDiff) < Math.toRadians(15.0)) {
                                    scratchObstacles.removeAt(i)
                                }
                            }
                        }
                    }

                    if (scratchObstacles.size > 50) {
                        scratchObstacles.subList(0, scratchObstacles.size - 50).clear()
                    }

                    scratchObstacles.toList()
                }

                state.copy(
                    obstacles = newList,
                    lastUpdateTimestampMs = action.timestampMs
                )
            }
            else -> state
        }
    }
}
