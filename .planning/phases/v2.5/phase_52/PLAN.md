# Phase 52: Elite Multi-Tag Variance Scaling & Distance Penalization (Kalman Noise Scaling) - Plan

## Proposed Changes

### Core Subsystems

#### [MODIFY] [PoseEstimator.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/math/PoseEstimator.kt)
- Add a global map of AprilTag coordinates: `1 to Pose3d(Translation3d(1.8, 1.8, 0.5), ...)`.
- Update `addVisionMeasurement` to accept an optional `numTags` argument defaulting to 1.
- Calculate the precise distance $d$ between the base pose estimate and the designated tag coordinate.
- Scale the base standard deviation vector dynamically: `scaledStdDevs = visionStdDevs * ( (1.0 / sqrt(numTags)) * sqrt(1.0 + d^2) )`.
- Generate the EKF measurement covariance matrix $R$ from these scaled standard deviations.

#### [MODIFY] [RootReducer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/reducer/RootReducer.kt)
- Pass `numTags = validMeasurements.size` inside the vision measurements iteration loop.

## Verification Plan

### Automated Tests
- Create unit tests in `PoseEstimatorTest.kt` verifying:
  - Base vision fusion runs normally at standard distance and single-tag defaults.
  - Measurement covariance scales quadratically as the robot distance from the tag grows (verifying lower EKF trust weight).
  - Multi-tag scaling reduces measurement covariance (verifying higher EKF trust weight).
- Execute `.\gradlew.bat test` to confirm clean builds and complete test success.
