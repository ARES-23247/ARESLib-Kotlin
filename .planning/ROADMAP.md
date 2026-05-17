# Roadmap: ARESLib-Kotlin

## Milestones

- ✅ **v1.0 MVP** — Phases 1-5 (shipped 2026-05-16)
- ✅ **v1.1 Driveable Base** — Phases 6-9 (shipped 2026-05-16)
- ✅ **v1.2 Deployable Mecanum Base** — Phases 10-12 (shipped 2026-05-16)

- ✅ **v1.3 Deployable Autonomy Base** — Phases 13-15 (shipped 2026-05-16)
- ✅ **v1.4 Desktop Simulation & Visualization** — Phase 16 (shipped 2026-05-16)
- ✅ **v1.5 Trajectory Following in Simulation** — Phase 17 (shipped 2026-05-16)

- ✅ **v1.7 Virtual Driver Station** — Phase 21 (shipped 2026-05-16)
- ✅ **v1.8 Vision & Localization** — Phases 22-25 (shipped 2026-05-17)
- ✅ **v1.9 Core Hardware IO Interfaces** — Phases 26-29 (shipped 2026-05-17)
- ✅ **v1.10 Match-Ready Telemetry & Hardware Integration** — Phases 30-33 (shipped 2026-05-17)

- ◆ **v2.0 Real Robot Deployment** — Phases 34-37

## Phases

### v2.0 Real Robot Deployment (Phases 34-37)

### Phase 34: Build Chain Unification & Mock Isolation
- **Goal:** Restructure Gradle modules so `:core` and `:ftc-hardware` are consumable by the Android build pipeline, and isolate mock FTC SDK stubs into a test-only source set.
- **Requirements:** BUILD-01, BUILD-02, BUILD-03, MOCK-01, MOCK-02, MOCK-03
- **Success criteria:**
  1. `gradlew.bat :TeamCode:installDebug` compiles without class conflicts
  2. Mock stubs are excluded from the Android APK classpath
  3. `gradlew.bat :core:test` and `gradlew.bat :simulator:run` still pass
  4. No duplicate class errors between `:core` stubs and FTC SDK

### Phase 35: Deployable TeleOp OpMode
- **Goal:** Create a properly annotated OpMode in TeamCode that wires the Redux state loop to real hardware drivers.
- **Requirements:** OPMODE-01, OPMODE-02, OPMODE-03, HW-01, HW-02, HW-03
- **Depends on:** Phase 34
- **Success criteria:**
  1. `@TeleOp(name="ARES Mecanum")` class exists in TeamCode source set
  2. OpMode calls `waitForStart()` and runs the read→reduce→calculate→write loop
  3. Hardware device names are documented in a config constant class
  4. Motor directions match standard mecanum convention

### Phase 36: Telemetry Hardening
- **Goal:** Make NT4 telemetry and data logging resilient to missing clients and Control Hub storage paths.
- **Requirements:** TEL-01, TEL-02, TEL-03
- **Depends on:** Phase 34
- **Success criteria:**
  1. NT4Telemetry catches WebSocket connection failures without crashing
  2. ARESDataLogger writes to Control Hub-appropriate storage path
  3. Telemetry exceptions are swallowed with a log warning, never blocking the control loop

### Phase 37: Deployment & Smoke Test
- **Goal:** Deploy APK to Control Hub and verify all wheels respond correctly to gamepad input.
- **Requirements:** DEPLOY-01, DEPLOY-02, DEPLOY-03
- **Depends on:** Phase 35, Phase 36
- **Success criteria:**
  1. `deploy.bat` pushes APK successfully over Wi-Fi ADB
  2. OpMode appears in Driver Station and initializes without crash
  3. All 4 mecanum wheels spin in correct direction for forward/strafe/rotate

<details>
<summary>✅ v1.10 Match-Ready Telemetry & Hardware Integration (Phases 30-33) — SHIPPED 2026-05-17</summary>

- [x] Phase 30: Advanced Controller Architecture (completed 2026-05-17)
- [x] Phase 31: NetworkTables 4 (NT4) Server (completed 2026-05-17)
- [x] Phase 32: Physical Vision & Hardware Configuration (completed 2026-05-17)
- [x] Phase 33: On-Device Data Logging (completed 2026-05-17)

</details>

<details>
<summary>✅ v1.9 Core Hardware IO Interfaces (Phases 26-29) — SHIPPED 2026-05-17</summary>

- [x] Phase 26: Core Hardware IO Interfaces (completed 2026-05-17)
- [x] Phase 27: FTC REV Hub & Pinpoint Integration (completed 2026-05-17)
- [x] Phase 28: I2C Auxiliary Wrappers (completed 2026-05-17)
- [x] Phase 29: Add absolute encoder wrappers (completed 2026-05-17)

</details>

<details>
<summary>✅ v1.8 Vision & Localization (Phases 22-25) — SHIPPED 2026-05-17</summary>

- [x] Phase 22: 3D Geometry and Transformations (completed 2026-05-17)
- [x] Phase 23: Vision State & Actions (completed 2026-05-17)
- [x] Phase 24: Pose Estimator (completed 2026-05-17)
- [x] Phase 25: Vision IO Layer Integration (completed 2026-05-17)

</details>

<details>
<summary>✅ v1.7 Virtual Driver Station (Phase 21) — SHIPPED 2026-05-16</summary>

- [x] Phase 21: Virtual Driver Station UI and Gamepad Overhaul (completed 2026-05-16)

</details>

<details>
<summary>✅ v1.6 Advanced Path Generation (Phases 18-20) — SHIPPED 2026-05-16</summary>

- [x] Phase 18: Spline Mathematics & Interpolation (1/1 plan) — completed 2026-05-16
- [x] Phase 19: Motion Profiling (1/1 plan) — completed 2026-05-16
- [x] Phase 20: Event Marker State Machine Integration (1/1 plan) — completed 2026-05-16

</details>

<details>
<summary>✅ v1.5 Trajectory Following in Simulation (Phase 17) — SHIPPED 2026-05-16</summary>

- [x] Phase 17: Holonomic Controller & Path Following Physics Loop (1 plan)

</details>

<details>
<summary>✅ v1.4 Desktop Simulation & Visualization (Phase 16) — SHIPPED 2026-05-16</summary>

- [x] Phase 16: Dyn4j Physics & AdvantageScope Telemetry (1 plan)

</details>

<details>
<summary>✅ v1.3 Deployable Autonomy Base (Phases 13-15) — SHIPPED 2026-05-16</summary>

- [x] Phase 13: PathPlanner JSON Parsing (1 plan)
- [x] Phase 14: Pure Holonomic Drive Controller (1 plan)
- [x] Phase 15: FTC Autonomous OpMode Integration (1 plan)

</details>

<details>
<summary>✅ v1.1 Driveable Base (Phases 6-9) — SHIPPED 2026-05-16</summary>

- [x] Phase 6: Gamepad Input Mapping (1 plan)
- [x] Phase 7: Hardware Odometry Bridge (1 plan)
- [x] Phase 8: Field-Centric Drivetrain (1 plan)
- [x] Phase 9: FTC Dashboard & Telemetry (1 plan)

</details>

<details>
<summary>✅ v1.2 Deployable Mecanum Base (Phases 10-12) — SHIPPED 2026-05-16</summary>

- [x] Phase 10: Mecanum Kinematics Engine (1 plan)
- [x] Phase 11: Mecanum Hardware IO (1 plan)
- [x] Phase 12: Deployable TeleOp Integration (1 plan)

</details>

## Progress

| Phase             | Milestone | Plans Complete | Status      | Completed  |
| ----------------- | --------- | -------------- | ----------- | ---------- |
| 1. Functional Core Scaffold | v1.0 | 1/1            | Complete    | 2026-05-15 |
| 2. FRC Bridge | v1.0      | 1/1            | Complete    | 2026-05-15 |
| 3. FTC Bridge | v1.0      | 1/1            | Complete    | 2026-05-16 |
| 4. Kinematics | v1.0      | 1/1            | Complete    | 2026-05-16 |
| 5. Autonomy | v1.0      | 1/1            | Complete    | 2026-05-16 |
| 6. Gamepad Input | v1.1 | 1/1 | Complete | 2026-05-16 |
| 7. Hardware Odom | v1.1 | 1/1 | Complete | 2026-05-16 |
| 8. Field-Centric Drive | v1.1 | 1/1 | Complete | 2026-05-16 |
| 9. Dashboard | v1.1 | 1/1 | Complete | 2026-05-16 |
| 10. Mecanum Math | v1.2 | 1/1 | Complete | 2026-05-16 |
| 11. Mecanum IO | v1.2 | 1/1 | Complete | 2026-05-16 |
| 12. TeleOp OpMode | v1.2 | 1/1 | Complete | 2026-05-16 |
| 13. Path Parsing | v1.3 | 1/1 | Complete | 2026-05-16 |
| 14. Drive Controller | v1.3 | 1/1 | Complete | 2026-05-16 |
| 15. Auto OpMode | v1.3 | 1/1 | Complete | 2026-05-16 |
| 16. Desktop Simulation | v1.4 | 1/1 | Complete | 2026-05-16 |
| 17. Trajectory Following in Sim | v1.5 | 1/1 | Complete | 2026-05-16 |
| 18. Spline Mathematics | v1.6 | 1/1 | Complete | 2026-05-16 |
| 19. Motion Profiling | v1.6 | 1/1 | Complete | 2026-05-16 |
| 20. Event Marker State Machine Integration | v1.6 | 1/1 | Complete | 2026-05-16 |

| 21. Virtual Driver Station | v1.7 | 1/1 | Complete | 2026-05-16 |
| 22. 3D Math | v1.8 | 1/1 | Complete | 2026-05-17 |
| 23. Vision State & Actions | v1.8 | 1/1 | Complete | 2026-05-17 |
| 24. Pose Estimator | v1.8 | 1/1 | Complete | 2026-05-17 |
| 25. IO Layer Integrations | v1.8 | 1/1 | Complete | 2026-05-17 |
| 26. Core Hardware IO Interfaces | v1.9 | 1/1 | Complete | 2026-05-17 |
| 27. FTC REV Hub & Pinpoint Integration | v1.9 | 1/1 | Complete | 2026-05-17 |
| 28. I2C Auxiliary Wrappers | v1.9 | 1/1 | Complete | 2026-05-17 |
| 29. Add absolute encoder wrappers | v1.9 | 1/1 | Complete | 2026-05-17 |
| 30. Advanced Controller Architecture | v1.10 | 1/1 | Complete | 2026-05-17 |
| 31. NetworkTables 4 (NT4) Server | v1.10 | 1/1 | Complete | 2026-05-17 |
| 32. Physical Vision & Hardware Configuration | v1.10 | 1/1 | Complete | 2026-05-17 |
| 33. On-Device Data Logging | v1.10 | 1/1 | Complete | 2026-05-17 |
| 34. Build Chain Unification & Mock Isolation | v2.0 | 1/1 | Complete | 2026-05-18 |
| 35. Deployable TeleOp OpMode | v2.0 | 1/1 | Complete | 2026-05-18 |
| 36. Telemetry Hardening | v2.0 | 1/1 | Complete | 2026-05-18 |
| 37. Deployment & Smoke Test | v2.0 | 0/1 | Pending | — |
