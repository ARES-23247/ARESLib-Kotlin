# ARESLib-Kotlin Core Module Audit Report

**Date:** 2026-07-04
**Auditor Name:** Antigravity (Lead Code Reviewer for Team ARES 23247)
**Scope:** `ARESLib-Kotlin/core` (`math`, `control`, `pathing`, `state`)

## Summary Scorecard

| Pillar | Grade | Critical Item Summary |
| :--- | :--- | :--- |
| 1. State Immutability & Redux Purity (R1) | B | Most state is immutable and reducers are pure, but `RobotFieldConfig` has mutable fields. |
| 2. Zero-GC Allocation in Hot-Paths (R2) | C | `PoseEstimator` (EKF) allocates objects heavily in hot loops; `LQRController` and `VFHPlanner` are flawless. |
| 3. Time-Determinism & Clock Purity (R3) | A | Perfect compliance. Uses `RobotClock` exclusively. No `System.currentTimeMillis()` found. |
| 4. Math Stability & Boundary Guards (R4) | A | Superb singularity protections in matrices and robust zero guards in `InputMath`. |
| 5. Hardware Timeout & Thread Purity (R5) | N/A | N/A (`core` is decoupled from hardware by design). |
| 6. API Design & KDoc Documentation | A | Excellent, championship-grade mathematical docs (DARE, EKF math explained clearly). |
| 7. Style & Conventions | A | Idiomatic Kotlin (e.g. trailing lambdas, `data class` usage) is prevalent. |
| 8. Testing Coverage & Verification | N/A | Not evaluated in this pass. |
| 9. Code Portability & Decoupling | A | 100% decoupled from FRC/FTC SDKs inside `core`. |
| 10. Memory Management & Object Pooling | A | `VFHPlanner` and `ThetaStarPlanner` use clever `ValleyPool` and `ThreadLocal` patterns. |
| 11. Logging Efficiency | N/A | Not evaluated in this pass. |
| 12. System Robustness & Failsafes | A | Control bounds checking (`LQRController` inputs) and watchdog warnings are well-implemented. |

---

## Sectioned Detail

### 1. State Immutability & Redux Purity (R1) 🔒
✅ **Strengths**
- Reducers (`RootReducer`) are 100% pure. They rely strictly on action payloads (e.g., `action.timestampMs`) and do not invoke external clocks or IO.
- State transitions correctly utilize the immutable `.copy()` pattern to create new state trees rather than mutating in place.
- Almost all fields in state data classes (like `RobotState`) are properly defined as read-only (`val`).

⚠️ **Findings**
- The `RobotFieldConfig` class has multiple mutable state fields which breaks the core invariant that state should only be updated via reducers and `.copy()`.

| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| R1-F01 | [HIGH] | Mutable `var vx = 0.0` and `var vy = 0.0` variables inside `RobotFieldConfig` mapping intent methods. | `RobotFieldConfig.kt:148,149` |
| R1-F02 | [HIGH] | Mutable `var activeConfig: RobotFieldConfig` acts as global mutable state. | `RobotFieldConfig.kt:181` |

### 2. Zero-GC Allocation in Hot-Paths (R2) ⚡
✅ **Strengths**
- `LQRController` uses completely pre-allocated vectors/matrices (`yMat`, `rawU`, `stateError`) during its `calculate()` loop. This is a championship-grade implementation of zero-allocation state space control.
- `VFHPlanner` correctly implements a `Valley` object pool to handle varying quantities of safe sectors without triggering heap allocations.

⚠️ **Findings**
- The EKF (`PoseEstimator`) suffers from multiple dynamic allocations inside high-frequency 50Hz-100Hz update loops, which will exhaust the GC budget on Android/RoboRIO runtimes.

| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| R2-F01 | [CRITICAL] | Instantiation of `Rotation2d`, `Pose2d`, and `Matrix3x3` inside `addOdometryObservation` during every odometry tick. | `PoseEstimator.kt:272-280` |
| R2-F02 | [CRITICAL] | Instantiation of new `Pose2d` and `Matrix3x3` inside `addVisionMeasurement`, along with a newly created `listOf(...)` for the Kalman gain. | `PoseEstimator.kt:538, 544` |
| R2-F03 | [MEDIUM] | The `RootReducer` dynamically allocates an `ArrayList<VisionMeasurement>` on every vision frame action. | `RootReducer.kt:23` |

### 3. Time-Determinism & Clock Purity (R3) ⏰
✅ **Strengths**
- The codebase correctly avoids direct `System.currentTimeMillis()` calls.
- `LQRController` effectively injects `RobotClock.currentTimeMillis()` for its watchdog reporting, ensuring simulation logging determinism.
- `RootReducer` correctly pulls timestamps directly from the dispatched `action.timestampMs` rather than querying the clock.

⚠️ **Findings**
- No issues found. Excellent compliance.

### 4. Math Stability & Boundary Guards (R4) 🎛️
✅ **Strengths**
- Matrix mathematical stability is outstanding. The `inverse()` method in `LQRController` and the EKF matrix inversions strictly check for singularity (`det <= 1e-12`, `isNaN`, `isInfinite`) and safely fall back to the Identity matrix.
- `InputMath.wrapAngle()` correctly utilizes a closed-form modulo operation (`(angleRad + Math.PI) % (2.0 * Math.PI)`) guaranteeing O(1) performance and protection against infinite while loops.
- Deadband calculations safely guard against division by zero (e.g. `denominator < 1e-6`).

⚠️ **Findings**
- No issues found. 

---

## Roadmap to Compliance

### 🔴 Must Fix
1. **[R2-F01 / R2-F02] Eliminate EKF Hot-Path Allocations:** Refactor `PoseEstimatorState` to use pre-allocated buffers or primitive arrays for its matrices and poses. Alternatively, implement a `copyInto(scratch)` pattern to avoid creating new `Matrix3x3` and `Pose2d` objects on every `addOdometryObservation` and `addVisionMeasurement` call.
2. **[R1-F01] Fix Mutability in Field Config:** Convert `var vx` and `var vy` to local immutable `val` declarations inside the `mapJoystickIntents` scope.

### 🟡 Should Fix
1. **[R2-F03] Avoid ArrayList Allocations in Reducer:** Instead of allocating `ArrayList<VisionMeasurement>` on every vision frame, try to pass pre-filtered arrays from the hardware IO layer or pool the lists.
2. **[R1-F02] Redux Manager State:** Move `activeConfig` into the overarching `RobotState` store instead of keeping it as a mutable singleton in `RobotFieldManager`.

### 🟢 Backlog
1. **ThetaStar Translation2d Instantiations:** Although `ThetaStarPlanner` is incredibly fast due to `ThreadLocal` buffers, it still allocates `Translation2d` objects during path reconstruction. Consider flattening coordinates into a primitive `DoubleArray` to make it 100% allocation-free.
