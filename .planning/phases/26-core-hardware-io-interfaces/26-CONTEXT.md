# Phase 26: Core Hardware IO Interfaces

## Domain
Implement abstract hardware interfaces in `:core` (`MotorIO`, `ServoIO`, `OdometryIO`, `ImuIO`) to decouple control logic from physical FTC hardware.

## Canonical Refs
- ROADMAP.md

## Locked Requirements
- Maintain 100% pure, immutable, testable control logic.
- Avoid FTC specific imports (`com.qualcomm`) in `:core`.
- Support translation to `RobotAction`s and from `RobotState`.

## Implementation Decisions
- Implement standard property-based interfaces for physical devices (e.g., `motor.power = X`).
- Expose read-only states for sensors (Odometry, IMU).
- Ensure garbage collection minimization via primitive-driven values.
