# Phase 8 Plan: Field-Centric Drivetrain

**Goal:** Transform human joystick inputs into field-centric drive speeds using the current odometry heading.
**Requirements covered:** DRIVE-01

## 1. Context & Architecture
In field-centric drive, pushing forward on the joystick always moves the robot away from the driver, regardless of the robot's current heading. To achieve this, the input translation vector (X/Y velocities) must be rotated by the inverse of the robot's current heading before being fed to the kinematics engine (which operates in robot-centric space).

## 2. Tasks

### [ ] 1. Add `fromFieldRelativeSpeeds` to `ChassisSpeeds`
- **Files:** `src/main/kotlin/com/areslib/math/ChassisSpeeds.kt`
- **Action:** Add a companion object function `fromFieldRelativeSpeeds` that takes field-centric `vx`, `vy`, `omega`, and `robotHeading` (as `Rotation2d` or radians) and returns a robot-centric `ChassisSpeeds`.

### [ ] 2. Update Drive Intent Reducer / Controller (if applicable)
- **Files:** (Conceptual integration in `rootReducer` or similar control loop). We will provide a pure utility `FieldCentricDriveController` that maps `JoystickDriveIntent` + `RobotState` -> `ChassisSpeeds`.

### [ ] 3. Unit Tests
- **Files:** `src/test/kotlin/com/areslib/math/ChassisSpeedsTest.kt`
- **Action:** Test that field-centric inputs at 0 heading map directly, and at 90 degrees they map orthogonally.

## 3. Review & Verification
- Verify the math runs cleanly without allocations.
