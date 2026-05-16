# Project Requirements

## Traceability

| ID | Phase | Status |
|---|---|---|
| AUTO-01 | | |
| AUTO-02 | | |
| AUTO-03 | | |

---

## Milestone v1.3 Requirements

### Autonomy Engine
- [ ] **AUTO-01**: User can parse a PathPlanner trajectory JSON file into an immutable `Path` data structure without relying on hardware SDKs.
- [ ] **AUTO-02**: System provides a pure `HolonomicDriveController` that accepts a `Path` and current `Pose2d` to output target `ChassisSpeeds`.
- [ ] **AUTO-03**: User can execute an autonomous routine on FTC hardware via a `LinearOpMode` that routes the `HolonomicDriveController` outputs to the `MecanumHardwareIO`.

---

## Future Requirements

- [ ] Path generation (dynamic pathing)
- [ ] Superstructure mechanism control (Elevator/Intake)
- [ ] Vision-based pose disambiguation (AprilTags)

## Out of Scope
- [ ] Mutable subsystem classes (Breaks purely functional paradigm)
- [ ] KAPT / AdvantageKit `@AutoLog` (Breaks Android compatibility)
