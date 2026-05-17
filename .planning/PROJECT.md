# ARESLib-Kotlin

## What This Is
ARESLib-Kotlin is a foundational, cross-platform (FTC and FRC) robotics library built with Kotlin 1.9+. It provides a highly performant, functional, and immutable state-driven core. By leveraging a Redux-style state store and an "IO Layer" pattern, it fully decouples pure control logic (such as Swerve, Mecanum, and Differential kinematics, and PathPlanner trajectory following) from hardware SDKs, ensuring 100% offline testability.

## Core Value
100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.

## Requirements

### Validated
- ✓ Core Functional Scaffold: Redux-style state store (RobotState, DriveState, SuperstructureState, VisionState) and immutable actions/reducers. — v1.0
- ✓ Base Interfaces: HardwareIO, RobotState, OutputCommand. — v1.0
- ✓ FTC SDK Bridge: "Hollow Wrapper" LinearOpMode that initializes the dispatcher and runs pure functions. — v1.0
- ✓ FTC IO Layer: SwerveModuleIO implementation translating REV Hub hardware maps into immutable state. — v1.0
- ✓ FRC CTRE CANivore "Airlock": Thin IO layer optimized for Phoenix 6 (`waitForUpdate` synchronization) dispatching to central store. — v1.0
- ✓ FRC Logging Adapter: Native WPILib Struct API serialization for AdvantageScope logging of data classes. — v1.0
- ✓ Kinematics Engines: Pure control logic for Swerve, Mecanum, and Differential drivetrains. — v1.0
- ✓ Functional PathPlanner Interpreter: Pure JSON parser and Trajectory follower (`calculateTrajectoryOutput`) returning Holonomic Drive Controller target speeds. — v1.0
- ✓ Gamepad & Input Mapping (Deadbands and curve mapping) — v1.1
- ✓ Hardware Odometry IO (goBILDA Pinpoint / OTOS wrapper) — v1.1
- ✓ Field-Centric Driving (Inverse rotation math) — v1.1
- ✓ FTC Dashboard Integration (Live telemetry and field rendering) — v1.1
- ✓ Mecanum Kinematics Math — v1.2
- ✓ Mecanum Hardware IO (DcMotorEx bridge) — v1.2
- ✓ Deployable TeleOp LinearOpMode — v1.2

- ✓ PathPlanner JSON parsing logic into immutable Path structs — v1.3
- ✓ Pure Holonomic Drive Controller for trajectory following — v1.3
- ✓ Autonomous OpMode for deploying paths on FTC hardware — v1.3

- ✓ Dyn4j Desktop Simulation & Visualization (AdvantageScope telemetry streaming) — v1.4
- ✓ Trajectory Following in Simulation (HolonomicDriveController + path sampling) — v1.5
- ✓ Quintic Hermite Spline calculation from waypoints. — v1.6
- ✓ Motion Profiling (Trapezoidal velocity profiles). — v1.6
- ✓ Integration of PathPlanner Event Markers as `RobotAction` triggers in the state machine. — v1.6
- ✓ Validation of smooth curved path following in the desktop simulator. — v1.6
- ✓ 3D coordinate data structures and matrix mathematics. — v1.8
- ✓ Immutable chronological array-backed Kalman Filter. — v1.8
- ✓ IO Layer abstraction for Limelight. — v1.8
- ✓ FtcRevHubIO, PinpointOdometryIO, I2C Octoquad wrappers — v1.9
- ✓ Absolute encoder wrapppers for CRServos — v1.9
### Active

## Current Milestone: v1.10 Match-Ready Telemetry & Hardware Integration

**Goal:** Implement physical hardware wrappers and advanced tuning/telemetry to prepare the robot for match play.

**Target features:**
- Advanced Gamepad/Controller Architecture with edge detection and custom triggers.
- Native NetworkTables 4 (NT4) Server for pure FTC/FRC debugging and live-tuning ecosystem unification.
- Physical Vision Hardware Wrappers (Limelight 3A & VisionPortal).
- Centralized RobotConfig mapping.
- On-Device SD Card WPILog/CSV Logging for post-match analysis.
### Out of Scope
- Mutable internal state within subsystems — breaks testability and the functional paradigm.
- AdvantageKit `@AutoLog` or KAPT usage — drastically increases build times and breaks cross-platform compatibility on Android.
- `com.qualcomm` or `edu.wpi` imports in pure logic files — tightly couples the logic to specific platforms.

## Context
- The library must be highly performant on the Android 9+ ART generational garbage collector, demanding minimized heap allocations within the main control loop by relying on short-lived immutable objects or value classes.
- For FRC, strict synchronization with the CANivore bus is required for zero odometry jitter.
- The project targets Kotlin 1.9+ features and standardizes on primitive-driven state handling.

## Constraints
- **Architecture**: Redux-style — State must be immutable and transitioned strictly via Actions and pure Reducers.
- **Garbage Collection**: Android ART GC constraints — Avoid heavy allocations in `opModeIsActive()` or `robotPeriodic()`.
- **Cross-Platform**: Code must build and run on both Android (FTC) and RoboRIO (FRC).
- **Tooling**: Pure data struct serialization for AdvantageScope rather than annotation generation.

## Key Decisions
| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Pure Functional Core | Maximizes testability and allows sharing 100% of mathematical logic between FTC and FRC. | — Pending |
| No KAPT / `@AutoLog` | Speeds up build times and ensures Android/RoboRIO cross-compatibility. | — Pending |
| Redux-style State Store | Centralizes all robot state, enabling time-travel debugging and unified logging. | — Pending |

---

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-16 after v1.6 milestone completion*
