# Phase 10 Plan: Mecanum Kinematics Engine

**Goal:** Convert `ChassisSpeeds` into individual wheel speeds (Front Left, Front Right, Back Left, Back Right) using pure math.
**Requirements covered:** MEC-01

## 1. Context & Architecture
Mecanum drive allows holonomic movement. The forward, strafe, and rotation velocities in `ChassisSpeeds` must be combined linearly to produce the necessary speed for each of the four wheels. The kinematics engine will be a pure Kotlin class that takes `ChassisSpeeds` and returns a `DoubleArray` or a custom `MecanumWheelSpeeds` data class containing the four speeds, ensuring the highest output does not exceed a maximum allowable speed (normalization).

## 2. Tasks

### [ ] 1. Define MecanumWheelSpeeds Data Class
- **Files:** `src/main/kotlin/com/areslib/kinematics/MecanumWheelSpeeds.kt`
- **Action:** Create a pure `data class MecanumWheelSpeeds(val frontLeft: Double, val frontRight: Double, val backLeft: Double, val backRight: Double)`.

### [ ] 2. Implement MecanumKinematics
- **Files:** `src/main/kotlin/com/areslib/kinematics/MecanumKinematics.kt`
- **Action:** Create `MecanumKinematics` with a `toWheelSpeeds(speeds: ChassisSpeeds): MecanumWheelSpeeds` function. Implement the standard mecanum drive equations and add normalization logic.

### [ ] 3. Unit Tests
- **Files:** `src/test/kotlin/com/areslib/kinematics/MecanumKinematicsTest.kt`
- **Action:** Verify that pure forward `ChassisSpeeds` results in all positive wheel speeds, pure strafe results in alternating speeds, and pure rotation results in one side positive, the other negative.

## 3. Review & Verification
- Verify `MecanumKinematics` runs cleanly without allocations and only utilizes `ChassisSpeeds` and primitive math.
