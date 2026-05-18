# Requirements - Milestone v3.0: FRC Marvin 19 Unified Robot Integration & Full System Verification

Active requirements for Milestone v3.0.

## Active Requirements

### Category: FRC Unified Robot Loop & Control
* [ ] **FRC-INT-01 (Unified Robot Loop)**: Integrate the Marvin 19 Superstructure Redux state and kinematic drivetrain into a unified `RobotState` store loop in `frc-app`, orchestrating the full robot lifecycle (disabled, autonomous, teleop) with standard driver gamepads.

### Category: AdvantageScope Visualizer Streaming
* [ ] **FRC-SIM-02 (AdvantageScope Drivetrain & Superstructure Stream)**: Stream 2D/3D state transitions, velocities, currents, and gravity-offset positions for the Marvin 19 drivetrain and superstructure subsystems (Flywheel, Cowl, Intake, Feeder, Climber) to AdvantageScope via NT4 for live visualization.

### Category: Decoupled Simulation Boundaries
* [ ] **FTC-SYS-01 (Mocks Separation)**: Relocate all pure simulated FTC stub classes from `:core` into a decoupled simulation module (`:ftc-mocks`), clean up `:TeamCode` OpModes, and ensure `deploy.bat` pushes cleanly to the REV Control Hub.

### Category: E2E Match Trajectory Simulation
* [ ] **SYS-TEST-01 (Autonomous Match Simulation)**: Author a complete FRC Autonomous Sequence (`ShootAndMobility`) that triggers path-following checkpoints, transitions intake pivots, regulates flywheels at 4000 RPM, triggers feeder sensors, and shoots fuel targets entirely in simulation.

---

## Traceability

| Requirement ID | Description | Mapped Phase | Status |
|----------------|-------------|--------------|--------|
| **FRC-INT-01** | Unified FRC Marvin 19 Robot Loop | Phase 72     | Planned|
| **FRC-SIM-02** | AdvantageScope Superstructure Stream | Phase 73     | Planned|
| **FTC-SYS-01** | Mocks Separation & Real OpMode Hardening | Phase 74     | Planned|
| **SYS-TEST-01**| Autonomous Match Simulation | Phase 75     | Planned|

---

## Future Requirements (Deferred)
- Automated battery-voltage current compensation on physical TalonFX loops — Deferred to v3.1 until basic loop integration is fully verified.

---

## Out of Scope
- Integration with third-party driver cameras in simulation — simulation focus remains strictly on structural physics, odometry, and state store transitions.
