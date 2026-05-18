# Phase 72: Unified FRC Marvin 19 Robot Loop - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Smart Autonomous Discuss

<domain>
## Phase Boundary

The goal of Phase 72 is to integrate the Redux state machine for the Marvin 19 superstructure and the Swerve drivetrain into a unified robot execution loop in `frc-app`. This loop will orchestrate the full robot lifecycle (Disabled, Autonomous, TeleOp) using standard Xbox controllers for user inputs, updating a single immutable `RobotState` container.

</domain>

<decisions>
## Implementation Decisions

### Drivetrain & Superstructure Synchronization
- A single Redux `Store` will hold `RobotState` (which already nests `SuperstructureState` and `SwerveState`).
- The `Robot` class (inheriting from WPILib's `TimedRobot` or `RobotBase`) will run at a deterministic 20ms update period.
- Periodically, the robot loop will:
  1. Read physical/simulated inputs (Joysticks, motor sensors, limit switches).
  2. Dispatch telemetry and sensor updates to the Redux store (updating encoder readings, joystick values, etc.).
  3. Run pure reducers to transition state based on active modes or user actions.
  4. Write the resulting state commands to physical Phoenix 6 (TalonFX) hardware interfaces or simulation stubs.

### TeleOp Mappings
- **Right Bumper (RB)**: Spins flywheel to target velocity (4000 RPM).
- **Right Trigger (RT)**: Runs Feeder/Transfer to shoot fuel when Flywheel is ready.
- **Left Bumper (LB)**: Toggles Intake deployment (extend/retract).
- **Left Trigger (LT)**: Runs Intake rollers.
- **Left Stick**: Field-relative Translation (X/Y).
- **Right Stick**: Field-relative Rotation (Yaw).

</decisions>

<code_context>
## Existing Code Insights

- `frc-app` contains FRC specific modules. Let's inspect `frc-app`'s source tree to find the existing `Robot.kt` or `Main.kt` files.
- We have `core` where `RobotState`, `SuperstructureReducer`, etc. reside.

</code_context>

<specifics>
## Specific Requirements

- Implement standard WPILib controller bindings mapping Xbox buttons to actions.
- Connect the Phoenix 6 Swerve drivetrain commands to updates in `SwerveState`.
- Bind `FlywheelState`, `IntakeState`, `FeederState` to Phoenix 6 actuators via their platform-agnostic `IO` interfaces (`FlywheelIO`, `CowlIO`, `IntakeIO`, `FeederIO`).

</specifics>
