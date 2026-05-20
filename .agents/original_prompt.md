# Original User Request

## Initial Request — 2026-05-20T02:49:28Z

Execute a comprehensive architectural hardening of the ARESLib-Kotlin robotics library across Math, State, Hardware IO, and App layers to achieve world-class fault tolerance.

Working directory: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin
Integrity mode: development

## Requirements

### R1. Core Math & Control Layer Hardening
Inject strict numerical bounds checking (NaN/infinity, division-by-zero, matrix singularities) into the EKF, LQR controllers, and Theta* pathfinder. Eliminate GC allocations in high-frequency hot-paths (e.g., `update()` loops, trajectory sampling) by using primitives and pre-allocated buffer pools. Implement integral windup limits and output clamping for all PID/Feedforward controllers.

### R2. State Management & Redux Engine
Conduct a deep immutability audit of all `RobotState` sub-states to ensure no mutable lists/maps exist. Ensure all reducers are pure functions that safely discard invalid `RobotAction` objects without throwing unhandled exceptions.

### R3. Hardware I/O Fault Tolerance
Implement read timeouts and fallback logic for I2C/UART sensors (Pinpoint, Gyro) to prevent loop hangs. Add automated motor current spike limits and stall detection (comparing encoder velocities against applied voltage).

### R4. Application Layer Failsafes
Wrap top-level OpMode iterations in fallback `try-catch` blocks that safely disable outputs and log telemetry instead of crashing. Implement a loop time watchdog that detects and logs overruns of the targeted 50Hz/100Hz budget.

## Acceptance Criteria

### Verification & Testing
- [ ] `.\gradlew.bat test` successfully compiles and passes 100% of the unit tests.
- [ ] EKF and Control math gracefully handles division-by-zero or extreme inputs without crashing or returning NaN.
- [ ] Motor timeout and sensor disconnect logic gracefully catches connection drops without freezing the main application thread.
- [ ] All `RobotState` classes are strictly immutable.
