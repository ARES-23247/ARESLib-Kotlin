# Plan - Phase 48: High-Fidelity Vision Simulation & Noise Rejection

Validate Kalman Filter pose corrections inside the dynamic physics simulator under added noise and out-of-order delay, ensuring zero tracking-state divergence or coordinate instability.

## Proposed Changes

### Core Vision Simulation

#### [NEW] [VisionSimulator.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/hardware/vision/VisionSimulator.kt)
- Represents a visual simulation engine containing the locations of 3D AprilTags on the field.
- Takes the true simulated pose and adds configurable Gaussian noise, latency, out-of-order jitter, and random high-ambiguity outliers.

### Desktop Simulator Wiring

#### [MODIFY] [DesktopSimLauncher.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/simulator/src/main/kotlin/com/areslib/sim/DesktopSimLauncher.kt)
- Instantiate `VisionSimulator` with FRC field AprilTag coordinates.
- Periodically poll `VisionSimulator` in the simulator loop using `currentPose` as ground truth.
- Dispatch `VisionMeasurementsReceived` to the `rootReducer`.
- Log estimated pose and true pose to Telemetry/AdvantageScope for visual regression checks.

### Testing

#### [NEW] [VisionNoiseRejectionTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/hardware/vision/VisionNoiseRejectionTest.kt)
- Implement rigorous integration tests using the EKF (`PoseEstimator`), `VisionOutlierFilter`, and `VisionSimulator`.
- Verify that under heavy noise and delay, the retroactive Kalman Filter converges toward the true simulated trajectory and completely rejects simulated outliers.

## Verification Plan

### Automated Tests
- Run unit tests:
  ```bash
  ./gradlew.bat :core:test --tests "com.areslib.hardware.vision.VisionNoiseRejectionTest"
  ```
