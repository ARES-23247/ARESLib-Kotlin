# Roadmap: ARESLib-Kotlin

## Milestones

- ✅ **v1.0 MVP** — Phases 1-5 (shipped 2026-05-16)
- ✅ **v1.1 Driveable Base** — Phases 6-9 (shipped 2026-05-16)
- ✅ **v1.2 Deployable Mecanum Base** — Phases 10-12 (shipped 2026-05-16)
- ✅ **v1.3 Deployable Autonomy Base** — Phases 13-15 (shipped 2026-05-16)
- ✅ **v1.4 Desktop Simulation & Visualization** — Phase 16 (shipped 2026-05-16)
- ✅ **v1.5 Trajectory Following in Simulation** — Phase 17 (shipped 2026-05-16)
- ✅ **v1.6 Advanced Path Generation** — Phases 18-20 (shipped 2026-05-16)
- ✅ **v1.7 Virtual Driver Station** — Phase 21 (shipped 2026-05-16)
- ✅ **v1.8 Vision & Localization** — Phases 22-25 (shipped 2026-05-17)
- ✅ **v1.9 Core Hardware IO Interfaces** — Phases 26-29 (shipped 2026-05-17)
- ✅ **v1.10 Match-Ready Telemetry & Hardware Integration** — Phases 30-33 (shipped 2026-05-17)
- ✅ **v2.0 Real Robot Deployment** — Phases 34-37 (shipped 2026-05-18)
- ✅ **v2.1 FRC CTRE Swerve Integration** — Phases 38-40 (shipped 2026-05-18)

- ✅ **v2.2 FRC Physics Simulation** — Phases 41-42 (shipped 2026-05-18)
- ◆ **v2.3 FRC Autonomous Trajectory Following** — Phases 43-44

## Phases

### v2.3 FRC Autonomous Trajectory Following (Phases 43-44)

### Phase 43: Autonomous Path Parsing & State Wiring
- **Goal:** Parse PathPlanner trajectories in the FRC app module and wire them to autonomous init.
- **Requirements:** AUTO-01, AUTO-02, AUTO-04
- **Success criteria:**
  1. Target JSON trajectory files load from project resources without crash
  2. Initial pose of trajectory correctly aligns/offsets the simulated drive state odometry

### Phase 44: Trajectory Follower Integration & Simulation Verification
- **Goal:** Drive the dyn4j simulated robot autonomously using the HolonomicDriveController.
- **Requirements:** AUTO-03, AUTO-05, AUTO-06
- **Depends on:** Phase 43
- **Success criteria:**
  1. Robot autonomously executes trajectory loops with stable force calculation
  2. AdvantageScope correctly displays the active Target vs. Actual poses in 3D space

<details>
<summary>✅ v2.2 FRC Physics Simulation (Phases 41-42) — SHIPPED</summary>

- [x] Phase 41: Simulation Core & Field Obstacles
- [x] Phase 42: Fuel Dynamics & Telemetry

</details>

<details>
<summary>✅ v2.1 FRC CTRE Swerve Integration (Phases 38-40) — SHIPPED</summary>

- [x] Phase 38: Initialize FRC App & Telemetry
- [x] Phase 39: Swerve Hardware IO Bridge
- [x] Phase 40: FRC Robot Loop & Simulation

</details>

<details>
<summary>✅ v2.0 Real Robot Deployment (Phases 34-37) — SHIPPED</summary>

- [x] Phase 34: Build Chain Unification & Mock Isolation
- [x] Phase 35: Deployable TeleOp OpMode
- [x] Phase 36: Telemetry Hardening
- [x] Phase 37: Deployment & Smoke Test

</details>

<details>
<summary>✅ v1.10 Match-Ready Telemetry & Hardware Integration (Phases 30-33) — SHIPPED</summary>

- [x] Phase 30: Advanced Controller Architecture
- [x] Phase 31: NetworkTables 4 (NT4) Server
- [x] Phase 32: Physical Vision & Hardware Configuration
- [x] Phase 33: On-Device Data Logging

</details>

<details>
<summary>✅ v1.9 Core Hardware IO Interfaces (Phases 26-29) — SHIPPED</summary>

- [x] Phase 26: Core Hardware IO Interfaces
- [x] Phase 27: FTC REV Hub & Pinpoint Integration
- [x] Phase 28: I2C Auxiliary Wrappers
- [x] Phase 29: Add absolute encoder wrappers

</details>

<details>
<summary>✅ v1.8 Vision & Localization (Phases 22-25) — SHIPPED</summary>

- [x] Phase 22: 3D Geometry and Transformations
- [x] Phase 23: Vision State & Actions
- [x] Phase 24: Pose Estimator
- [x] Phase 25: Vision IO Layer Integration

</details>

## Progress

| Phase             | Milestone | Plans Complete | Status      | Completed  |
| ----------------- | --------- | -------------- | ----------- | ---------- |
| ... | | | | |
| 34. Build Chain Unification & Mock Isolation | v2.0 | 1/1 | Complete | 2026-05-18 |
| 35. Deployable TeleOp OpMode | v2.0 | 1/1 | Complete | 2026-05-18 |
| 36. Telemetry Hardening | v2.0 | 1/1 | Complete | 2026-05-18 |
| 37. Deployment & Smoke Test | v2.0 | 1/1 | Complete | 2026-05-18 |
| 38. Initialize FRC App & Telemetry | v2.1 | 1/1 | Complete | 2026-05-18 |
| 39. Swerve Hardware IO Bridge | v2.1 | 1/1 | Complete | 2026-05-18 |
| 40. FRC Robot Loop & Simulation | v2.1 | 1/1 | Complete | 2026-05-18 |
| 41. Simulation Core & Field Obstacles | v2.2 | 1/1 | Complete | 2026-05-18 |
| 42. Fuel Dynamics & Telemetry | v2.2 | 1/1 | Complete | 2026-05-18 |
| 43. Autonomous Path Parsing & State Wiring | v2.3 | 0/1 | Pending | — |
| 44. Trajectory Follower Integration & Simulation Verification | v2.3 | 0/1 | Pending | — |

