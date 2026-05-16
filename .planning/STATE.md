# Project State

## Project Reference
See: .planning/PROJECT.md (updated 2026-05-15)

**Core value:** 100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.
**Current focus:** Pending execution of Phase 7

## Session Memory
Phase 6 (Gamepad Input Mapping) is complete. The pure math utilities `InputMath` handle deadbands and exponents without allocations, and `GamepadIO` translates raw FTC sticks into `JoystickDriveIntent`.
Proceeding with Phase 7: Hardware Odometry Bridge.
