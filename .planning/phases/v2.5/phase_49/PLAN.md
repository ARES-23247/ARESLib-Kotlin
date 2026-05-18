# Phase 49: State Expansion & IMU Pitch/Roll Telemetry - Plan

## Proposed Changes

### Core Subsystems

#### [MODIFY] [RobotState.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/state/RobotState.kt)
- Expand `DriveState` with the following properties:
  - `pitchDegrees: Double = 0.0`
  - `rollDegrees: Double = 0.0`
  - `xAccelerationG: Double = 0.0`
  - `yAccelerationG: Double = 0.0`
  - `zAccelerationG: Double = 0.0`

#### [MODIFY] [RobotAction.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/action/RobotAction.kt)
- Add default-initialized fields to `DriveHardwareUpdate` and `PoseUpdate` for pitch, roll, and accelerations, preventing breaking changes on existing test suites.

#### [MODIFY] [RootReducer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/reducer/RootReducer.kt)
- Copy `pitchDegrees`, `rollDegrees`, `xAccelerationG`, `yAccelerationG`, and `zAccelerationG` from the actions into `DriveState` within both the `DriveHardwareUpdate` and `PoseUpdate` branches.

## Verification Plan

### Automated Tests
- Run Gradle builds via `./gradlew.bat test` to verify the codebase compiles completely and all existing unit tests pass cleanly.
- Add an explicit unit test in `RootReducerTest.kt` validating that IMU telemetry is successfully propagated into `RobotState` upon dispatch.
