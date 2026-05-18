# Phase 62: Dual-State Offline Replay Runner & AdvantageScope Ghost Telemetry Summary

## Objective
Implement an offline, memory-buffered sensory EKF playback runner and an NT4 streaming server to visualize twin trajectory paths (Real vs. Ghost) inside AdvantageScope.

## Implementation Details

### Dual-State Playback Runner
* **SensoryReplayRunner**: Implemented a raw log parsing engine that reads `.jsonl` logged frames into high-speed memory buffers.
* **Twin State Pipeline**: Runs EKF calculations on two tracks simultaneously:
  1. **Real State**: The original pose estimate computed on the robot during the actual match.
  2. **Ghost State**: A recalculated pose estimate using modified vision filter thresholds or noise covariances, demonstrating "what-if" scenarios.

### NT4 AdvantageScope Publisher
* **ReplayPublisher**: Integrated NetworkTables 4 bindings to stream dual-pose traces, 3D coordinate geometry, and target timestamps at maximum rate.
* **AdvantageScope Visualizer**: Enables direct overlay comparison of real trajectories vs. ghost trajectories on a visual FTC Field canvas.

## Verification
* Executed E2E simulation playback and NT4 streaming checks:
  ```powershell
  .\gradlew.bat test --tests com.areslib.logging.SensoryReplayRunnerTest
  ```
* **Status**: `BUILD SUCCESSFUL`
