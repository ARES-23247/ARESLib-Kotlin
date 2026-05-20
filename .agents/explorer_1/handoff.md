# Handoff Report: Math, Control, and Pathing Hardening

## Observation
- **Numerical Bounds (EKF & LQR):** 
  - In `PoseEstimator.kt` (line 305), `val S_inv = S.inverse()` is called without checking if `S` is singular, risking NaNs or crashes.
  - In `LQRController.kt` (lines 292-381), `Matrix.inverse()` enforces singularity checks via `require(abs(det) > 1e-12) { "Matrix is singular" }`. This throws an `IllegalArgumentException` which will crash the robot's control loop instead of degrading gracefully.
  - In `ThetaStarPlanner.kt` (lines 52-53), `start` and `end` are checked for NaN/Infinity, but `costmap.origin.x` and `costmap.origin.y` are not checked before use in grid coordinate math.
- **GC Allocations (EKF & Theta*):**
  - In `PoseEstimator.kt` `addOdometryObservation` (runs at high Hz), lines 183-193 allocate new `Matrix3x3`, `Rotation2d`, and `Pose2d` instances using operator overloads (`*`, `+`). It also allocates a new `PoseEstimatorState` via `state.copy()` (line 198) on every update.
  - In `ThetaStarPlanner.kt` (lines 66-68), a new `PriorityQueue`, `HashSet`, and `HashMap` are allocated on every single `plan()` call.
- **PID/FF Windup & Clamping:**
  - `PIDController.kt` already implements windup limits (lines 90-91) and output clamping (lines 98-99). However, `HolonomicDriveController.kt` (lines 73-75) instantiates PID controllers but never configures their integral ranges or output limits, leaving them vulnerable to windup.
  - `GravityFeedforward.kt` (lines 16-32) performs raw output calculations (`kG * cos(angleRadians)`) without any output clamping parameters.

## Logic Chain
1. The use of `require()` in `LQRController.kt`'s matrix inversion is dangerous for real-time control, as sensor noise could produce a singular matrix and crash the JVM. It should be replaced with safe NaN-propagation or returning a zero-matrix. Similarly, `PoseEstimator` needs a singularity guard before `S.inverse()`.
2. The `PoseEstimator` odometry loop is a high-frequency hot-path. Operator overloads (`+`, `*`) create hidden object allocations, causing GC pauses. Replacing these with in-place mutating methods (e.g., `addInto`, which `LQRController` already uses) will eliminate this pressure.
3. `ThetaStarPlanner`'s local collection variables should be hoisted to object-level properties and `clear()`'d before each run to avoid heavy collection instantiations.
4. While `PIDController` contains the mathematical logic for anti-windup, its wrappers (like `HolonomicDriveController`) fail to utilize it. `GravityFeedforward` lacks the mechanism entirely. Injecting configuration bounds in these wrappers/functions fulfills the prompt's requirement.

## Caveats
- Investigation was scoped to the specified files in `com.areslib.math`, `com.areslib.control`, and `com.areslib.pathing`. Other subsystems calling these controllers were not analyzed for proper initialization.
- As a read-only agent, no code changes were applied or tested.

## Conclusion
- **EKF/LQR:** Refactor `Matrix.inverse()` in `LQRController` to handle singularities gracefully (return zero matrix/error flag) instead of throwing. Add `S.determinant()` singularity guards in `PoseEstimator.kt`.
- **GC Allocations:** Convert `PoseEstimator` to update a pre-allocated state object and use in-place matrix operations (`addInto()`). Promote `ThetaStarPlanner`'s `openSet`/`closedSet`/`nodeMap` to object properties and `.clear()` them per `plan()` call.
- **PID/FF:** Add `setOutputLimits()` and `setIntegratorRange()` initialization logic inside `HolonomicDriveController.kt`. Modify `GravityFeedforward.calculateElevator` and `calculateArm` to accept and enforce `minClamp`/`maxClamp` arguments.

## Verification Method
1. Make the recommended changes to the target `.kt` files.
2. Run `./gradlew test` (or the project's test command) to verify mathematical correctness is preserved.
3. To verify GC elimination, run a simulated odometry loop or `ThetaStarPlanner.plan()` continuously within an Android Studio / VisualVM profiler to confirm 0 bytes of memory are allocated per tick.
