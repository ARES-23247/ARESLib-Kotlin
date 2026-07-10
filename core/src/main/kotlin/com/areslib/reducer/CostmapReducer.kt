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
                state
            }
            else -> state
        }
    }
}
