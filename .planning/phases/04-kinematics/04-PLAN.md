# Phase 4 Plan: Kinematics

**Goal:** Pure functional kinematics engine for holonomic mapping
**Requirements covered:** KIN-01, KIN-02, KIN-03

## 1. Context & Architecture
In this phase, we implement the math layer. Since Android ART GC pauses can impact control loop frequencies, we must avoid creating short-lived objects in tight loops (like matrix multiplications). We will implement `SwerveKinematics` as a pure Kotlin class.

## 2. Tasks

### [ ] 1. Base Geometry and Kinematics Primitives
- **Files:** `src/main/kotlin/com/areslib/math/Geometry.kt`, `src/main/kotlin/com/areslib/math/ChassisSpeeds.kt`, `src/main/kotlin/com/areslib/kinematics/SwerveModuleState.kt`
- **Action:** Implement `Translation2d`, `Rotation2d`, `ChassisSpeeds` and `SwerveModuleState`.

### [ ] 2. Pure Functional Swerve Kinematics
- **Files:** `src/main/kotlin/com/areslib/kinematics/SwerveKinematics.kt`
- **Action:** Implement `toSwerveModuleStates(speeds: ChassisSpeeds): Array<SwerveModuleState>`. The calculation must be purely mathematical using the inverse kinematics matrix, isolated from hardware.

### [ ] 3. Kinematics Tests
- **Files:** `src/test/kotlin/com/areslib/kinematics/SwerveKinematicsTest.kt`
- **Action:** Verify that applying a pure forward `ChassisSpeeds` correctly maps to identical drive speeds and 0 steer angles for all modules.

## 3. Review & Verification
- Verify `toSwerveModuleStates` relies on no hardware state.
- Verify zero side effects.
- Run `gradle test`.
