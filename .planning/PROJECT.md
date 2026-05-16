# ARESLib-Kotlin

## What This Is
ARESLib-Kotlin is a foundational, cross-platform (FTC and FRC) robotics library built with Kotlin 1.9+. It provides a highly performant, functional, and immutable state-driven core. By leveraging a Redux-style state store and an "IO Layer" pattern, it fully decouples pure control logic (such as Swerve, Mecanum, and Differential kinematics, and PathPlanner trajectory following) from hardware SDKs, ensuring 100% offline testability.

## Core Value
100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.

## Requirements

### Validated
(None yet — ship to validate)

### Active
- [ ] Core Functional Scaffold: Redux-style state store (RobotState, DriveState, SuperstructureState, VisionState) and immutable actions/reducers.
- [ ] Base Interfaces: HardwareIO, RobotState, OutputCommand.
- [ ] FTC SDK Bridge: "Hollow Wrapper" LinearOpMode that initializes the dispatcher and runs pure functions.
- [ ] FTC IO Layer: SwerveModuleIO implementation translating REV Hub hardware maps into immutable state.
- [ ] FRC CTRE CANivore "Airlock": Thin IO layer optimized for Phoenix 6 (`waitForUpdate` synchronization) dispatching to central store.
- [ ] FRC Logging Adapter: Native WPILib Struct API serialization for AdvantageScope logging of data classes.
- [ ] Kinematics Engines: Pure control logic for Swerve, Mecanum, and Differential drivetrains.
- [ ] Functional PathPlanner Interpreter: Pure JSON parser and Trajectory follower (`calculateTrajectoryOutput`) returning Holonomic Drive Controller target speeds.

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
*Last updated: 2026-05-15 after initialization*
