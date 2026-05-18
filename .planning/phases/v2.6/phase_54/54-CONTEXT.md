# Phase 54: Distance Sensor Local Costmap Integration - Context

We are implementing a 2D local costmap in the Redux state tree, fusing distance sensor scans (observations) into coordinate points in the robot's field-relative frame using the current estimated pose.

## Architectural Choices

### Costmap Representation
- Create an `Obstacle` class representing a local coordinate (X, Y) obstacle position.
- Create `CostmapState` inside `RobotState` to store the list of active obstacles.
- Define `DistanceSensorObservation` containing the range reading, sensor mounting offsets (angle and translation offsets from the robot center), and max detection range.

### State Transitions
- Define `RobotAction.ObstacleCostmapUpdate` to receive distance sensor scans.
- In `rootReducer`, process these scans using the current EKF estimated pose to project local distances to field-relative coordinate locations.
- Implement clear-space raycasting: when a sensor reports maximum range, clear obstacles along that line of sight to allow dynamic obstacle clearing.

## Key Files to Create/Modify
- `RobotState.kt` (Add `CostmapState`, `Obstacle`)
- `RobotAction.kt` (Add `ObstacleCostmapUpdate`, `DistanceSensorObservation`)
- `RootReducer.kt` (Handle `ObstacleCostmapUpdate` in reducer state machine)
