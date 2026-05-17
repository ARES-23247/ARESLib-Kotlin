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

- ◆ **v2.2 FRC Physics Simulation** — Phases 41-42

## Phases

### v2.2 FRC Physics Simulation (Phases 41-42)

### Phase 41: Simulation Core & Field Obstacles
- **Goal:** Integrate dyn4j world and static 2026 REBUILT obstacles into the FRC app.
- **Requirements:** PHYS-01, PHYS-02, PHYS-03, PHYS-04
- **Success criteria:**
  1. `dyn4j` World initializes in the `frc-app` loop
  2. Robot correctly translates `ChassisSpeeds` to physical forces
  3. Robot body collides with field walls and REBUILT Hubs instead of driving through them

### Phase 42: Fuel Dynamics & Telemetry
- **Goal:** Spawn fuel pieces and link physics to AdvantageScope telemetry.
- **Requirements:** FUEL-01, FUEL-02
- **Depends on:** Phase 41
- **Success criteria:**
  1. Fuel rigid bodies populate the physics world
  2. Robot pushes Fuel around using dyn4j physical collisions
  3. AdvantageScope displays Fuel correctly in 3D space via the `Robot/FuelPoses` network table array

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
| 41. Simulation Core & Field Obstacles | v2.2 | 0/1 | Pending | — |
| 42. Fuel Dynamics & Telemetry | v2.2 | 0/1 | Pending | — |

