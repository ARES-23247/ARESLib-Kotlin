# Phase 5 Plan: Functional Autonomy

**Goal:** PathPlanner JSON interpreter and pure trajectory following
**Requirements covered:** AUTO-01, AUTO-02

## 1. Context & Architecture
In this final v1 phase, we implement the autonomous logic. PathPlanner exports `.path` files in JSON format. To avoid WPILib's `Filesystem` dependencies (which don't exist on FTC Android), we will define pure Kotlin domain models for a `Trajectory` and `TrajectoryState`. The file I/O will be left to the hardware-specific layers (e.g. `AppUtil` on FTC, `Filesystem` on FRC), which will read the file into a pure JSON string and pass it to our pure `PathParser`. We will also build a `HolonomicDriveController` that takes current state and target state and returns pure `ChassisSpeeds`.

## 2. Tasks

### [ ] 1. Trajectory Domain Models
- **Files:** `src/main/kotlin/com/areslib/auto/Trajectory.kt`
- **Action:** Define `Trajectory` and `TrajectoryState` (timeSeconds, velocity, pose, etc.) using `Translation2d` and `Rotation2d`.

### [ ] 2. Pure JSON Parser (Stub)
- **Files:** `src/main/kotlin/com/areslib/auto/PathParser.kt`
- **Action:** Create `PathParser.fromJson(jsonString: String): Trajectory`. Since we aren't pulling in Jackson or Moshi at this core level right now, we will just create a stub implementation that returns a mock trajectory to prove the architectural boundary.

### [ ] 3. Holonomic Drive Controller
- **Files:** `src/main/kotlin/com/areslib/auto/HolonomicDriveController.kt`
- **Action:** Implement pure mathematical PID/Feedforward logic: `fun calculate(currentPose: Translation2d, currentHeading: Rotation2d, targetState: TrajectoryState): ChassisSpeeds`.

### [ ] 4. Pure Autonomy Tests
- **Files:** `src/test/kotlin/com/areslib/auto/HolonomicDriveControllerTest.kt`
- **Action:** Test that if the robot is exactly at the target pose, the output `ChassisSpeeds` is zero. Test that if it is behind, it outputs positive forward velocity.

## 3. Review & Verification
- Verify `PathParser` takes a `String` (not a `File`).
- Verify `calculate` is a pure function.
- Run `gradle test`.
