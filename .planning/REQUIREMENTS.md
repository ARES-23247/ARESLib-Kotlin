# Requirements

## Active

### Path Parsing & Setup
- [ ] **AUTO-01**: Provide deterministic, thread-safe loading of PathPlanner JSON files in `frc-app` at robot initiation.
- [ ] **AUTO-02**: Map path structures to standard `Trajectory` representation compatible with our Redux states.

### Autonomous Control Loop
- [ ] **AUTO-03**: Integrate the functional `HolonomicDriveController` into FRC `autonomousPeriodic()` loops.
- [ ] **AUTO-04**: Support start-state position reset (odometry alignment) based on initial trajectory points.
- [ ] **AUTO-05**: Implement state-driven path-action commands (events triggers) during path following.

### Trajectory Diagnostics
- [ ] **AUTO-06**: Stream target path translation poses array (`Robot/TargetPose`) and active trajectory deviations (`Robot/TrajectoryError`) to AdvantageScope.

## Traceability
*Updated by roadmap generation.*
