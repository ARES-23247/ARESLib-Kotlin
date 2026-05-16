# Project Requirements

## Traceability

| ID | Phase | Status |
|---|---|---|
| MEC-01 | Phase 10 | Complete |
| MEC-02 | Phase 11 | Complete |
| TELE-01 | Phase 12 | Complete |

---

## Milestone v1.2 Requirements

### Mecanum Drivetrain
- [ ] **MEC-01**: A `MecanumKinematics` class that converts `ChassisSpeeds` into individual wheel speeds (Front Left, Front Right, Back Left, Back Right) using pure math.
- [ ] **MEC-02**: A `MecanumHardwareIO` class that interfaces with the FTC SDK's `HardwareMap` to apply voltages to four `DcMotorEx` instances based on calculated wheel speeds.

### Deployable TeleOp
- [ ] **TELE-01**: A complete, functional `LinearOpMode` named `ARESMecanumTeleOp` that glues together `GamepadIO`, `PinpointIO`, the root Redux store, and `MecanumHardwareIO` into a real, deployable control loop.

---

## Future Requirements

- [ ] Path generation (dynamic pathing)
- [ ] Superstructure mechanism control (Elevator/Intake)
- [ ] Vision-based pose disambiguation (AprilTags)
- [ ] Autonomous sequence scheduler

## Out of Scope
- [ ] Mutable subsystem classes (Breaks purely functional paradigm)
- [ ] KAPT / AdvantageKit `@AutoLog` (Breaks Android compatibility)
