# Project Retrospective

## Milestone: v1.1 — Driveable Base, Hardware Odometry & Telemetry

**Shipped:** 2026-05-16
**Phases:** 4 | **Plans:** 4

### What Was Built
1. Gamepad Input Mapping (Deadbands and curves via `InputMath`)
2. Hardware Odometry Bridge (`PinpointIO`, `OdometryMath`, `PoseUpdate`)
3. Field-Centric Drivetrain (`ChassisSpeeds.fromFieldRelativeSpeeds`)
4. FTC Dashboard & Telemetry (`FtcDashboardAdapter` and `TelemetryPacket` formatting)

### What Worked
- Extending the purely functional IO pattern to cover the Gamepad and the goBILDA Pinpoint driver proved exceptionally easy.
- Keeping math entirely pure and isolated allowed tests to confirm field-centric and odometry operations immediately without hardware.

### Key Lessons
- Providing standalone primitive-driven components (like `FtcDashboardAdapter` consuming `RobotState`) keeps the core library extremely portable. Android ART compatibility is maintained because there are zero allocations in the control loop.
