# Phase 50: Advanced Outlier Filter (3D Boundaries, Angular Speed, Acceleration/Shock Lockouts) - Plan

## Proposed Changes

### Core Subsystems

#### [MODIFY] [VisionOutlierFilter.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/hardware/vision/VisionOutlierFilter.kt)
- Expand `VisionFilterConfig` with fields for field boundaries ($X, Y, Z$), angular velocity threshold, and acceleration threshold.
- Update `isValid` method signature with default parameters for angular velocity and 3D linear accelerations.
- Implement checking:
  - 3D spatial boundaries of the target pose.
  - Angular velocity lockout (to prevent motion blur).
  - High-G shock lockout (to protect against camera shake).

#### [MODIFY] [RootReducer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/reducer/RootReducer.kt)
- Update `VisionMeasurementsReceived` reduction to extract dynamic IMU variables from `state.drive` and pass them to `filter.isValid(...)`.

## Verification Plan

### Automated Tests
- Create dedicated unit tests in `VisionOutlierFilterTest.kt` validating:
  - Absolute field X and Y boundary rejection.
  - Z vertical limits rejection (underground / floating).
  - Angular velocity motion blur rejection.
  - Dynamic linear shock acceleration collision lockout.
- Execute the test suite via `.\gradlew.bat test` to confirm everything is functionally sound and 100% correct.
