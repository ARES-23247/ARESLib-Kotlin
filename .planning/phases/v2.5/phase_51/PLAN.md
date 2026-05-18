# Phase 51: Dynamic Odometry Covariance Scaling (IMU Tilt & Beaching Safe) - Plan

## Proposed Changes

### Core Subsystems

#### [MODIFY] [PoseEstimator.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/math/PoseEstimator.kt)
- Update `addOdometryObservation` to accept `pitchDegrees` and `rollDegrees` default arguments.
- Compute the tilt magnitude `sqrt(pitchDegrees * pitchDegrees + rollDegrees * rollDegrees)`.
- If tilt exceeds 15.0 degrees (beaching lockout), freeze the pose update (zero translation and heading changes) and do not grow the covariance matrix.
- If tilt exceeds 8.0 degrees (slippage expected), scale the process noise matrix $Q$ by $100.0$ during covariance propagation.

#### [MODIFY] [RootReducer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/reducer/RootReducer.kt)
- Pass `pitchDegrees` and `rollDegrees` from `DriveHardwareUpdate` into `PoseEstimator.addOdometryObservation`.

## Verification Plan

### Automated Tests
- Create dedicated unit tests in `PoseEstimatorTest.kt` verifying:
  - Default odometry updates operate normally under flat orientation (0 degrees).
  - Covariance scales by $100\times$ under high tilt (e.g. 10 degrees pitch).
  - Odometry pose freezes and covariance does not grow under beaching tilt (e.g. 20 degrees roll).
- Run `.\gradlew.bat test` to verify complete build and test success.
