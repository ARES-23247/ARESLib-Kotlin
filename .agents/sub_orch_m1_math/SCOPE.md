# Scope: MathAndControlHardening

## Architecture
- `com.areslib.math`: Math primitives, EKF.
- `com.areslib.control`: PID, Feedforward, LQR controllers.
- `com.areslib.pathing`: Theta* pathfinding.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1.1 | Estimation | Inject numerical bounds checking (NaN/infinity, division-by-zero, matrix singularities) into EKF. Eliminate GC allocations in update loops. | none | PLANNED |
| 1.2 | Control | Inject numerical bounds checking and singularity checks into LQR. Add integral windup limits and output clamping for PID/Feedforward. Eliminate GC in update hot-paths. | none | PLANNED |
| 1.3 | Pathing | Inject numerical bounds checking into Theta*. Eliminate GC allocations in trajectory sampling by using primitives and pre-allocated buffer pools. | none | PLANNED |

## Interface Contracts
### `math` ↔ `control`
- Controllers and filters should throw custom exceptions or gracefully reject invalid inputs containing NaNs or Infinities.
- `update()` methods must not allocate objects during normal execution.
