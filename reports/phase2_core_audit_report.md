# Phase 2 Core Audit Report

**Date:** 2026-07-04
**Auditor:** Lead Code Reviewer, Team ARES 23247
**Scope:** `ARESLib-Kotlin/core` Module (Focus: Pathfinding, Control loops, Math Stability, GC Allocations)

## 📊 Summary Scorecard

| Pillar | Grade | Critical Item summary |
| :--- | :--- | :--- |
| 1. State Immutability & Redux Purity (R1) | N/A | Not evaluated in this scope. |
| 2. Zero-GC Allocation in Hot-Paths (R2) | C | `Translation2d` heap allocations in Theta*; `Matrix` instantiations in dynamic LQR solver. |
| 3. Time-Determinism & Clock Purity (R3) | A | Utilizes `RobotClock` safely where applicable. |
| 4. Math Stability & Boundary Guards (R4) | A+ | Exemplary singularity guards in matrix inversion and robust division-by-zero protections in pathing/control. |
| 5. Hardware Timeout & Thread Purity (R5) | N/A | Mocked/Abstracted outside of core logic. |
| 6. API Design & KDoc Documentation | A | Strong inline KDoc for all algorithms and matrices. |
| 7. Style & Conventions (Kotlin-First) | A | High usage of idiomatic Kotlin. |
| 8. Testing Coverage & Verification | N/A | Not evaluated in this scope. |
| 9. Code Portability & Decoupling | A | Completely decoupled from Android/WPILib physical dependencies. |
| 10. Memory Management & Object Pooling | B+ | VFH+ and Theta* utilize excellent ThreadLocal and custom pools, but slightly marred by `Translation2d`. |
| 11. Logging Efficiency | N/A | Not evaluated in this scope. |
| 12. System Robustness & Failsafes | A | Fallback identities in LQR inversion prevent fatal exceptions. |

---

## 📋 Sectioned Detail

### 2. Zero-GC Allocation in Hot-Paths (R2) ⚡
✅ **Strengths:**
- **VFH+ Sector Valley Pooling:** `VFHPlanner.kt` strictly adheres to zero-GC by using primitive arrays (`sectors`, `smoothedSectors`, `candidatesBuffer`) and a highly efficient pre-allocated `Valley` object pool array. 
- **LQR `calculate()` Purity:** The high-frequency `LQRController.calculate()` loop executes perfectly without creating a single object, utilizing pre-allocated matrix buffers (`yMat`, `kTimesError`, `bTimesU`, etc.) and `multiplyInto`/`addInto` functions.
- **Theta* Search Purity:** `ThetaStarPlanner.kt` utilizes a pre-allocated `ThreadLocal` `PlannerState` class for its graph arrays and open heap, preventing node allocation during the A* expansion phase.

⚠️ **Findings:**
- **Theta* Path Reconstruction Allocations:** While the search phase is clean, `ThetaStarPlanner.kt` allocates a dynamically sized `mutableListOf<Translation2d>()` and instantiates new `Translation2d(fx, fy)` objects on the heap during `reconstructPath()`. This violates strict zero-GC guidelines if pathing is run at a high control frequency.
- **Dynamic DARE Solver Allocations:** `LQRController.computeFeedbackGains()` instantiates heavily via `Matrix.add()`, `Matrix.multiply()`, and `Matrix.inverse()` within a `maxIterations` loop. If this is intended to run dynamically on the loop to recompute gains, it will cause immense GC pressure and thrashing.

### 4. Math Stability & Boundary Guards (R4) 🎛️
✅ **Strengths:**
- **LQR Matrix Singularity Protection:** `LQRController.kt` handles inverse calculations beautifully with protections for 1x1, 2x2, 3x3, and N-dimensional systems. It checks `if (abs(det) <= 1e-12 || det.isNaN() || det.isInfinite())` and safely falls back to an Identity matrix, printing a system warning instead of crashing the control thread.
- **VFH+ Division Guards:** `VFHPlanner.computeDetourHeading()` evaluates `(aConstant - bConstant * distance) / distance`, which is mathematically safe because a preceding line `if (distance > sensingRangeMeters || distance < 0.05) continue` explicitly filters out zero/negligible distance obstacles. 
- **Theta* Grid Scaling Guards:** `ThetaStarPlanner.plan()` correctly guards against `costmap.resolutionMeters <= 0.0` at the very beginning, returning an empty path before any division by zero can occur during Cartesian-to-grid mapping.
- **Brownout Division Protection:** `BrownoutGuard.kt` properly checks `if (range <= 0.0)` to bypass interpolation division by zero.
- **LQR dt Protection:** `LQRController.calculate()` verifies `dtSeconds > 0.0` and ensures `y` and `xRef` inputs are finite before running.

⚠️ **Findings:**
- No major math stability violations detected. The math handling is incredibly robust and championship-grade.

---

## 🚨 Findings Table

| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| TAG-F01 | [HIGH] | `Translation2d` heap allocations inside `reconstructPath` loop. | `ThetaStarPlanner.kt:290` |
| TAG-F02 | [HIGH] | Heavy `Matrix` instantiations inside `maxIterations` loop of the dynamic DARE solver. | `LQRController.kt:96-107` |

---

## 🗺️ Roadmap to Compliance

- 🔴 **Must Fix:** Refactor `ThetaStarPlanner.reconstructPath` to utilize a pre-allocated array pool of `Translation2d` objects or raw `DoubleArray`s (for x and y coordinates) bounded by maximum path length, rather than a dynamically expanding `mutableListOf`.
- 🔴 **Must Fix:** Clarify the usage of `LQRController.computeFeedbackGains()`. If it is meant to run periodically dynamically, it must be rewritten to use the `*Into` pre-allocated matrix buffers. If it is purely an initialization function, document it as such.
- 🟢 **Backlog:** Create a global object pool for coordinate representations (`Translation2d`, `Pose2d`) to eliminate the need for heap instantiation across the entire `com.areslib.pathing` subsystem.
