# Requirements: ARESLib-Kotlin

## Milestone v1.10: Match-Ready Telemetry & Hardware Integration

### Category: Advanced Controller Architecture
- [ ] **CTRL-01**: The system must provide a unified `Controller` interface that wraps raw hardware inputs (FTC Gamepad or FRC GenericHID).
- [ ] **CTRL-02**: The controller must support button state tracking (isPressed, isHeld, isReleased).
- [ ] **CTRL-03**: The controller must support edge-detection event triggers (onTrue, onFalse) for seamless command/action binding.
- [ ] **CTRL-04**: Analog triggers must be configurable with thresholds to act as digital buttons.

### Category: NetworkTables 4 (NT4) Server
- [ ] **NT-01**: A pure Kotlin, Android-compatible NT4 server (or WebSocket implementation) must be built or integrated to run on the FTC Control Hub.
- [ ] **NT-02**: The NT4 server must publish robot state (Pose2d, velocities, hardware states) to standard NT4 paths recognizable by AdvantageScope.
- [ ] **NT-03**: The NT4 server must subscribe to tuning variables (e.g., PID gains) to allow live adjustments from FRC Dashboards (like Elastic or Shuffleboard).

### Category: Hardware Integration & Vision
- [ ] **HW-01**: Create physical vision wrappers for Limelight 3A and FTC VisionPortal (AprilTags) that implement the `VisionIO` abstraction.
- [ ] **HW-02**: Implement a centralized `RobotConfig` or `RobotMap` pattern to map physical hardware ports to abstract IO interfaces cleanly.

### Category: Data Logging
- [ ] **LOG-01**: Implement an on-device data logger that writes telemetry to the Control Hub's local storage (SD Card) in either CSV or WPILog format for post-match analysis.

## Future Requirements (Deferred)
- Tournament API integrations
- Multi-robot fleet configuration syncing

## Out of Scope
- FTC Dashboard Integration (Explicitly excluded to maintain pure platform-agnostic architecture and unify around the FRC NT4 ecosystem).

## Traceability
*(To be filled by Roadmap)*
