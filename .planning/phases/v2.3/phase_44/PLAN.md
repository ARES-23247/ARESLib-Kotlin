# Plan: Phase 44 (Trajectory Follower Integration & Simulation Verification)

## Focus
Implement high-fidelity autonomous trajectory tracking inside the FRC app module (`frc-app`) using our `HolonomicDriveController` and verify it in simulated physics.

## Proposed Changes

### `frc-app` Code Modifications

#### [MODIFY] [ARESRobot.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/frc-app/src/main/kotlin/com/areslib/frc/ARESRobot.kt)
- **Controller Initialization**: Instantiate `HolonomicDriveController` using robust closed-loop PID gains:
  - X Position PID: `P = 4.0`, `I = 0.0`, `D = 0.1`
  - Y Position PID: `P = 4.0`, `I = 0.0`, `D = 0.1`
  - Heading Theta PID: `P = 3.0`, `I = 0.0`, `D = 0.0`
- **Distance Tracking**: Track `autoDistance` dynamically based on target velocity feedforward:
  - Initialize `private var autoDistance = 0.0`
- **`autonomousPeriodic()`**:
  - Compute elapsed loop `dt`.
  - Sample target `PathPoint` from `activePath` at current `autoDistance`.
  - Calculate robot-centric target velocities using `HolonomicDriveController`.
  - Convert output velocities to field-relative speeds to step dyn4j.
  - Update `autoDistance += targetPoint.velocityMps * dt`.

## Verification Plan

### Automated Verification
- Run `./gradlew :frc-app:build` to confirm there are no syntax or type compilation errors.

### Manual Verification
- In the simulation execution, verify that the robot cleanly tracks the entire spline trajectory from `(2,2)` through `(5,4)` and terminates at `(8,2)` without drift or oscillation.
