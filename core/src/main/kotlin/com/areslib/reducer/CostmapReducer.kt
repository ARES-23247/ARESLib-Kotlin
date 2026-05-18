package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.CostmapState
import com.areslib.state.Obstacle
import com.areslib.math.Pose2d

object CostmapReducer {
    /**
     * Reduces the CostmapState slice based on obstacle distance scanning relative to robot coordinate frame.
     */
    fun reduce(state: CostmapState, action: RobotAction, robotPose: Pose2d): CostmapState {
        return when (action) {
            is RobotAction.ObstacleCostmapUpdate -> {
                val robotX = robotPose.x
                val robotY = robotPose.y
                val robotHeadingRad = robotPose.heading.radians

                val currentObstacles = state.obstacles.toMutableList()

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
                        val existingIdx = currentObstacles.indexOfFirst { 
                            kotlin.math.hypot(it.x - obstacleFieldX, it.y - obstacleFieldY) < mergeThreshold 
                        }

                        val newObstacle = Obstacle(obstacleFieldX, obstacleFieldY)
                        if (existingIdx >= 0) {
                            currentObstacles[existingIdx] = newObstacle
                        } else {
                            currentObstacles.add(newObstacle)
                        }
                    } else {
                        currentObstacles.removeAll { obstacle ->
                            val dx = obstacle.x - sensorFieldX
                            val dy = obstacle.y - sensorFieldY
                            val dist = kotlin.math.hypot(dx, dy)
                            val angleToObstacle = kotlin.math.atan2(dy, dx)
                            
                            var angleDiff = angleToObstacle - sensorFieldAngle
                            while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI
                            while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI
                            
                            dist < obs.maxRangeMeters && kotlin.math.abs(angleDiff) < Math.toRadians(15.0)
                        }
                    }
                }

                if (currentObstacles.size > 50) {
                    while (currentObstacles.size > 50) {
                        currentObstacles.removeAt(0)
                    }
                }

                state.copy(
                    obstacles = currentObstacles,
                    lastUpdateTimestampMs = action.timestampMs
                )
            }
            else -> state
        }
    }
}
