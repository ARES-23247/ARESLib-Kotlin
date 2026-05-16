# Project State

## Project Reference
See: .planning/PROJECT.md (updated 2026-05-15)

**Core value:** 100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.
**Current focus:** Pending execution of Phase 4

## Session Memory
Phase 3 (FTC Hardware Bridge) is complete. The `ARESLibOpMode` serves as a hollow wrapper, passing the `HardwareMap` to the IO layer and simply dispatching loop cycles to the pure `rootReducer`. `SwerveModuleIOFtc` extracts and packages state locally without blocking for CAN.
Proceeding with Phase 4: Kinematics.
