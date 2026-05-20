# Scope: Milestone 1.3: Pathing Hardening

## Architecture
- Target: `core/src/main/kotlin/com/areslib/pathing/ThetaStarPlanner.kt`

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1.3 | Inject strict numerical bounds checking (NaN/infinity, division-by-zero) into the Theta* pathfinder (`ThetaStarPlanner.kt`). Eliminate GC allocations in trajectory sampling by using primitives and pre-allocated buffer pools. | none | IN_PROGRESS |
