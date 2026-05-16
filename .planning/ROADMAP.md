# Roadmap: ARESLib-Kotlin

## Milestones

- ✅ **v1.0 MVP** — Phases 1-5 (shipped 2026-05-16)
- ✅ **v1.1 Driveable Base** — Phases 6-9 (shipped 2026-05-16)
- ✅ **v1.2 Deployable Mecanum Base** — Phases 10-12 (shipped 2026-05-16)

- 🔄 **v1.3 Deployable Autonomy Base** — Phases 13-15 (active)

## Phases

### Phase 13: PathPlanner JSON Parsing (v1.3)
**Goal:** Parse `.path` JSON files from PathPlanner into immutable `Path` and `PathPoint` data structures.
**Requirements:** AUTO-01
**Success criteria:**
1. A JSON parser can read PathPlanner format and extract waypoints.
2. The `Path` struct provides interpolation between points.
3. Does not use `edu.wpi` or FRC SDK classes.

### Phase 14: Pure Holonomic Drive Controller (v1.3)
**Goal:** Implement a pure mathematical controller that compares current pose to path target and outputs velocities.
**Requirements:** AUTO-02
**Success criteria:**
1. Accepts current `Pose2d`, target `Pose2d`, and target speeds.
2. Employs PID or feedforward math to output `ChassisSpeeds`.
3. Output is fully stateless.

### Phase 15: FTC Autonomous OpMode Integration (v1.3)
**Goal:** Deploy the path follower to FTC hardware via a LinearOpMode.
**Requirements:** AUTO-03
**Success criteria:**
1. `ARESMecanumAuto` LinearOpMode is created.
2. It runs the Redux loop, feeding odometry into the HolonomicDriveController.
3. The resulting chassis speeds are routed through kinematics to hardware.

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
| 13. Path Parsing | v1.3 | 0/1 | Not started | — |
| 14. Drive Controller | v1.3 | 0/1 | Not started | — |
| 15. Auto OpMode | v1.3 | 0/1 | Not started | — |
