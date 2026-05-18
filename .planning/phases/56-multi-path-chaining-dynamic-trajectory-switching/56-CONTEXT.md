# Phase 56: Multi-Path Chaining & Dynamic Trajectory Switching - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers robust multi-path trajectory chaining and dynamic detour trajectory switching. Specifically, it enables:
1. Parsing and stiching multiple PathPlanner paths sequentially, ensuring smooth velocity and heading transitions at joint intersection boundaries.
2. Real-time trajectory switching (detouring) that intercepts current path-following execution and transitions smoothly to a dynamic avoidance path when costmap obstacles are triggered.

</domain>

<decisions>
## Implementation Decisions

### Trajectory Stitching & Chaining Strategy
- **D-01:** Joint boundaries between chained paths will blend velocity and interpolate orientation smoothly over a dynamic transition overlap region, preventing high-g actuator saturation or sudden acceleration spikes at path intersections.

### Dynamic Detour Trajectory Interception
- **D-02:** When a path detour is triggered, the system will perform an *immediate tangent arc* interception: calculating a smooth path from the robot's active real-time state to the detour trajectory, preventing coordinate snaps.

### Trigger API & State Management
- **D-03:** Path switching and detour overrides will be dispatched as centralized Redux actions (`SwitchPathAction`, `ChainPathsAction`) that update `RobotState.pathState` to maintain complete mathematical determinism and AdvantageScope playback compatibility.

### Distance Cumulative Modeling
- **D-04:** Chained paths will merge their distance fields into a single, continuous, cumulative `distanceMeters` timeline by shifting subsequent path point distances starting from the boundary join distance.

### Claude's Discretion
- Claude has full discretion over the mathematical blending window size (defaulting to 0.1–0.2 seconds or 0.1–0.3 meters of overlap region) and path spline tangent weight tuning for smooth detour generation.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Pathing & Parse Utilities
- `.planning/ROADMAP.md` — Active phases and milestones targets.
- `.planning/REQUIREMENTS.md` — Traceability and requirements validation targets (CHAIN-01, CHAIN-02).
- `core/src/main/kotlin/com/areslib/pathing/Path.kt` — Trajectory and point data structures.
- `core/src/main/kotlin/com/areslib/pathing/PathPlannerParser.kt` — Parse logic for PathPlanner files.

### Kinematics & Chassis Control
- `core/src/main/kotlin/com/areslib/control/HolonomicDriveController.kt` — Current feedback/feedforward tracking loops.
- `core/src/main/kotlin/com/areslib/pathing/VFHPlanner.kt` — VFH+ obstacle avoidance and detour candidate generation.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PathPlannerParser`: Can be reused/expanded to load and join multiple `.path` assets sequentially.
- `HolonomicDriveController`: Reused directly for trajectory tracking on stitched/detour paths.
- `VFHPlanner`: Reused to construct the detour heading target when costmap blocking alerts occur.

### Established Patterns
- Redux-style Reducers: The path state and active path selection are managed purely inside the state store, transitioning via Actions.
- Structural AdvantageScope Logging: Telemetry classes serialize directly via structured properties to NT4/SD logging.

### Integration Points
- `PathState`: Add `activePath`, `chainedPathsList`, and `detourActive` fields to trace path execution.
- `RootReducer`: Wired to handle `SwitchPathAction` and `ChainPathsAction`.

</code_context>

<specifics>
## Specific Ideas
- The detour path should blend smoothly from the current state rather than resetting path elapsed time, allowing the controller's integral/derivative states to remain continuous.

</specifics>

<deferred>
## Deferred Ideas
- **CHAIN-03 (Dynamic Spline Fit)**: Real-time generation of multi-point cubic/quintic splines directly on-device (deferred to v3.0).

</deferred>

---

*Phase: 56-Multi-Path Chaining & Dynamic Trajectory Switching*
*Context gathered: 2026-05-18*
