# Project State

## Project Reference
See: .planning/PROJECT.md (updated 2026-05-15)

**Core value:** 100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.
**Current focus:** Pending execution of Phase 9

## Session Memory
Phase 8 (Field-Centric Drivetrain) is complete. `ChassisSpeeds.fromFieldRelativeSpeeds` successfully performs the inverse rotation on field velocities to get robot-centric commands, enabling field-centric driving when given a live odometry heading.
Proceeding with Phase 9: FTC Dashboard & Telemetry.
