# Scope: Milestone 1.2: Control Hardening

## Architecture
- `PIDController.kt`, `LQRController.kt`, `GravityFeedforward.kt`
- Hardening numerical bounds, zero GC allocations in `update()`.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Control Hardening | Inject strict numerical bounds checking, matrix singularity checks, integral windup limits, output clamping. Eliminate GC allocations in `update()` loops. | none | BLOCKED: Quota Exceeded |

## Interface Contracts
### General
- The interfaces for `update()` methods should not allocate new objects. 
- Input/Output matrices and vectors should be modified in place or use pre-allocated buffers.
