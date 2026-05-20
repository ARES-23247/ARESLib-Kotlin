# Scope: Milestone 1.1: Estimation Hardening

## Architecture
- `com.areslib.math`: Math primitives, EKF.
- Target files: `PoseEstimator.kt`, `Matrix3x3.kt`

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1.1 Implementation | Inject numerical bounds checking (NaN/infinity, division-by-zero, matrix singularities) into EKF. Eliminate GC allocations in update loops. | none | IN_PROGRESS |

## Interface Contracts
- Must not allocate objects during normal execution.
- Throw custom exceptions or gracefully reject invalid inputs containing NaNs or Infinities.
