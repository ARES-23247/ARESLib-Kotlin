# Plan - Phase 47: Extended Kalman Filter Integration

Integrate the `VisionOutlierFilter` into the global `rootReducer` to gate EKF vision fusion and vision state updates.

## Proposed Changes

### Core Reducers

#### [MODIFY] [RootReducer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/reducer/RootReducer.kt)
- Instantiate `VisionOutlierFilter` in `RobotAction.VisionMeasurementsReceived` block.
- Retrieve the current robot pose estimate and heading.
- Filter incoming measurements using the outlier filter.
- Update EKF state and dispatch filtered action to `VisionReducer`.

### Testing

#### [NEW] [RootReducerTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/reducer/RootReducerTest.kt)
- Create unit tests verifying that a valid measurement gets fused into the robot state.
- Verify that an outlier measurement is discarded, resulting in no change to the robot state.

## Verification Plan

### Automated Tests
- Run unit tests:
  ```bash
  ./gradlew.bat :core:test --tests "com.areslib.reducer.RootReducerTest"
  ```
