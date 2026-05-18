# Phase 54: Distance Sensor Local Costmap Integration - Plan

Implementation checklist for fusing distance sensor observations into a coordinate costmap.

## Proposed Changes

### Core State & Actions

#### [MODIFY] [RobotState.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/state/RobotState.kt)
- Add `Obstacle` class with `x`, `y`, and `radius`.
- Add `CostmapState` data class with a list of `obstacles` and `lastUpdateTimestampMs`.
- Wire `costmap: CostmapState = CostmapState()` inside `RobotState`.

#### [MODIFY] [RobotAction.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/action/RobotAction.kt)
- Add `DistanceSensorObservation` holding range, offset translation/rotation, and max range limit.
- Add `ObstacleCostmapUpdate` payload.

#### [MODIFY] [RootReducer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/reducer/RootReducer.kt)
- Import needed classes.
- Handle `ObstacleCostmapUpdate` to project local raw ranges to field coordinate points based on the EKF pose.
- Implement basic line-of-sight obstacle clearing/pruning.

### Automated Verification

#### [NEW] [CostmapReducerTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/reducer/CostmapReducerTest.kt)
- Test coordinate projection yields exact trigonometry locations.
- Test dynamic distance updates prune or add obstacles correctly.
