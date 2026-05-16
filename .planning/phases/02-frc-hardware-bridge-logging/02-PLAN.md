# Phase 2 Plan: FRC Hardware Bridge & Logging

**Goal:** CTRE CANivore integration and native AdvantageScope serialization
**Requirements covered:** FRC-01, FRC-02

## 1. Context & Architecture
In this phase, we build the "Airlock" – a thin hardware input/output (IO) layer optimized for an all-CTRE Phoenix 6 drivetrain on a CANivore bus for FRC. We also implement a pure WPILib `Struct<T>` serializer so AdvantageScope can log our pure Kotlin data classes natively without relying on KAPT or `@AutoLog`. To preserve cross-platform purity, these specific implementations will live in a distinct `com.areslib.frc` package that can be excluded or separated when building for FTC.

## 2. Tasks

### [ ] 1. Define FRC Dependencies & Sub-packages
- **Files:** `build.gradle.kts`
- **Action:** We will stub out the necessary FRC dependencies (WPILib API, CTRE Phoenix 6) in the Gradle build script (using compileOnly to keep the library decoupled). 

### [ ] 2. Define IO Interfaces
- **Files:** `src/main/kotlin/com/areslib/frc/SwerveModuleIO.kt`
- **Action:** Create `SwerveInputs` data class (using primitive doubles only) and `SwerveModuleIO` interface defining the contract.

### [ ] 3. Implement Phoenix 6 "Airlock" (waitForUpdate)
- **Files:** `src/main/kotlin/com/areslib/frc/SwerveModuleIOPhoenix6.kt`
- **Action:** Implement `SwerveModuleIOPhoenix6` using `TalonFX` objects. 
- **Constraints:** Must use `BaseStatusSignal.waitForUpdate` mechanism to block and ensure zero odometry jitter before dispatching. Implement a `dispatchHardwareUpdate` function that packs the synchronized, timestamped inputs into a `RobotAction` and sends it to the store.

### [ ] 4. Native AdvantageScope Serializer
- **Files:** `src/main/kotlin/com/areslib/frc/RobotStateStruct.kt`
- **Action:** Implement WPILib's `Struct<RobotState>`. Manually serialize the nested `RobotState` primitives into a `java.nio.ByteBuffer` to demonstrate zero-boilerplate `@AutoLog`-free logging.

## 3. Review & Verification
- Verify `SwerveModuleIOPhoenix6` correctly synchronizes via `waitForUpdate`.
- Verify `RobotStateStruct` correctly maps primitive fields to ByteBuffer without reflection.
- Run `gradle test` to ensure it compiles successfully.
