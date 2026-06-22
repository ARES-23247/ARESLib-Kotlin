package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.CostmapState
import com.areslib.state.Obstacle
import com.areslib.math.Pose2d

object CostmapReducer {

    /**
     * Reduces the CostmapState slice based on obstacle distance scanning relative to robot coordinate frame.
     * Pure function: uses only local state, no shared mutable fields.
     */
    fun reduce(state: CostmapState, action: RobotAction, robotPose: Pose2d): CostmapState {
        return when (action) {
            is RobotAction.ObstacleCostmapUpdate -> {
                val robotX = robotPose.x
                val robotY = robotPose.y
                val robotHeadingRad = robotPose.heading.radians

                val workingList = ArrayList<Obstacle>(state.obstacles.size + action.observations.size)
                for (i in 0 until state.obstacles.size) {
                    workingList.add(state.obstacles[i])
                }

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
                        for (i in 0 until workingList.size) {
                            val it = workingList[i]
                            if (kotlin.math.hypot(it.x - obstacleFieldX, it.y - obstacleFieldY) < mergeThreshold) {
                                existingIdx = i
                                break
                            }
                        }

                        val newObstacle = Obstacle(obstacleFieldX, obstacleFieldY)
                        if (existingIdx >= 0) {
                            workingList[existingIdx] = newObstacle
                        } else {
                            workingList.add(newObstacle)
                        }
                    } else {
                        for (i in workingList.size - 1 downTo 0) {
                            val obstacle = workingList[i]
                            val dx = obstacle.x - sensorFieldX
                            val dy = obstacle.y - sensorFieldY
                            val dist = kotlin.math.hypot(dx, dy)
                            val angleToObstacle = kotlin.math.atan2(dy, dx)
                            val angleDiff = com.areslib.math.InputMath.wrapAngle(angleToObstacle - sensorFieldAngle)
                            if (dist < obs.maxRangeMeters && kotlin.math.abs(angleDiff) < Math.toRadians(15.0)) {
                                workingList.removeAt(i)
                            }
                        }
                    }
                }

                if (workingList.size > 50) {
                    workingList.subList(0, workingList.size - 50).clear()
                }

                state.copy(
                    obstacles = workingList.toList(),
                    lastUpdateTimestampMs = action.timestampMs
                )
            }
            else -> state
        }
    }
}
