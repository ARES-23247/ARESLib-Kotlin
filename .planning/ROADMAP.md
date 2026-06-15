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
- ✅ **v2.3 FRC Autonomous Trajectory Following** — Phases 43-44 (shipped 2026-05-18)
- ✅ **v2.4 FRC/FTC Vision & Multi-Sensor Kalman Filter Integration** — Phases 45-48 (shipped 2026-05-18)
- ✅ **v2.5 Hardened EKF Localization & Dynamic Sensor Fusion** — Phases 49-52 (shipped 2026-05-18)
- ✅ **v2.6 Dynamic Swerve Trajectory Optimization & Obstacle Avoidance** — Phases 53-55 (shipped 2026-05-18)
- ✅ **v2.7 Path Execution & Dynamic Task Planning** — Phases 56-59 (shipped 2026-05-18)
- ✅ **v2.8 Deterministic Input Replay & "What-If" Ghost Simulation** — Phases 60-63 (shipped 2026-05-18)
- ✅ **v2.9 Physical Deployment & FRC Marvin 19 Redux Migration** — Phases 64-71 (shipped 2026-05-18)
- ✅ **v3.0 FRC Marvin 19 Unified Robot Integration & Full System Verification** — Phases 72-74 (shipped 2026-06-15)
- 🚧 **v3.1 FTC EKF Localization Hardening & FRC E2E Match Simulation** — Phases 74.1-75 (in progress)

## Phases

### 🚧 v3.1 FTC EKF Localization Hardening & FRC E2E Match Simulation (Phases 74.1-75)

- [ ] Phase 74.1: Subsystem Modularity & Student Loop Facade
- [ ] Phase 74.2: FTC EKF Reset Alignment & Yaw Lockout Correction (INSERTED)
- [ ] Phase 75: FRC Autonomous Match E2E Simulation

### Phase 74.1: Subsystem Modularity & Student Loop Facade

**Goal**: Implement a clean vertical-slice subsystem modularity design and an intuitive student-facing loop facade to simplify state, action dispatching, and background telemetry.
**Mode**: Smart Autonomous Discuss

### Phase 74.2: FTC EKF Reset Alignment & Yaw Lockout Correction (INSERTED)

**Goal**: Correct FTC EKF estimated pose overrides when resetting offsets, and widen the Limelight yaw rejection gate to 30.0 degrees to prevent permanent EKF lockouts under drift.
**Mode**: Smart Autonomous Discuss

### Phase 75: FRC Autonomous Match E2E Simulation

**Goal**: Run end-to-end simulated FRC autonomous matches.
**Mode**: Smart Autonomous Discuss

<details>
<summary>✅ Legacy Milestones (v1.0 to v3.0) — SHIPPED</summary>

- [x] Phases 1-74 completed successfully and archived.

</details>

## Progress

| Phase             | Milestone | Plans Complete | Status      | Completed  |
| ----------------- | --------- | -------------- | ----------- | ---------- |
| 74.1 Subsystem Modularity & Student Loop Facade | v3.1 | 0/1 | Not started | -          |
| 74.2 FTC EKF Reset Alignment & Yaw Lockout Correction | v3.1 | 0/1 | Not started | -          |
| 75. FRC Autonomous Match E2E Simulation | v3.1 | 0/1 | Not started | -          |
