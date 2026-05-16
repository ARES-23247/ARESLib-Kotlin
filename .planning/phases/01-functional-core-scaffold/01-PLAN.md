# Phase 1 Plan: Functional Core Scaffold

**Goal:** Establish Redux-style store and pure state boundaries
**Requirements covered:** CORE-01, CORE-02, CORE-03, CORE-04

## 1. Context & Architecture
This phase establishes the foundational pure Kotlin architecture. Since this is a greenfield cross-platform library, we first need to initialize the build system. Then, we implement the strict immutable state tree, actions, and pure reducers. Crucially, there will be zero hardware SDK dependencies (no `com.qualcomm` or `edu.wpi` imports) in these core files to ensure 100% portability.

## 2. Tasks

### [ ] 1. Initialize Kotlin Project
- **Files:** `build.gradle.kts`, `settings.gradle.kts`
- **Action:** Scaffold a standard pure Kotlin JVM library project.
- **Constraints:** Ensure Kotlin 1.9+ is targeted. No FTC or FRC SDK dependencies allowed at this layer.

### [ ] 2. Define Base Functional Interfaces
- **Files:** `src/main/kotlin/com/areslib/core/HardwareIO.kt`, `src/main/kotlin/com/areslib/core/OutputCommand.kt`
- **Action:** Define the `HardwareIO` interface (for reading sensors) and `OutputCommand` interface (for motor voltages/targets).
- **Constraints:** Must be completely decoupled from specific hardware platforms.

### [ ] 3. Implement Immutable State Tree
- **Files:** `src/main/kotlin/com/areslib/state/RobotState.kt`, `src/main/kotlin/com/areslib/state/DriveState.kt`, `src/main/kotlin/com/areslib/state/SuperstructureState.kt`, `src/main/kotlin/com/areslib/state/VisionState.kt`
- **Action:** Create nested data classes. `DriveState` should be optimized for Holonomic/Swerve (e.g., holding module states and chassis speeds). `VisionState` should be timestamp-indexed.
- **Constraints:** 100% immutable (use `val`). Zero mutable properties.

### [ ] 4. Define Robot Actions
- **Files:** `src/main/kotlin/com/areslib/action/RobotAction.kt`
- **Action:** Create a sealed class `RobotAction` representing all possible state transitions. Include actions for hardware inputs (e.g., `HardwareUpdateAction` with sensor data) and human intents (e.g., `JoystickDriveAction`).
- **Constraints:** Actions must be immutable data carriers.

### [ ] 5. Implement Pure Reducer
- **Files:** `src/main/kotlin/com/areslib/reducer/RootReducer.kt`
- **Action:** Write `rootReducer(state: RobotState, action: RobotAction): RobotState`. Implement `when` statement over the sealed class to return a `copy()` of the state. Include basic mock odometry integration logic just to prove the concept.
- **Constraints:** Must be a pure function. Zero side effects.

### [ ] 6. Verification Tests
- **Files:** `src/test/kotlin/com/areslib/reducer/RootReducerTest.kt`
- **Action:** Write JUnit tests proving that the `rootReducer` processes actions correctly and does not mutate the original state object.

## 3. Review & Verification
- Verify all data classes use `val` exclusively.
- Verify `rootReducer` is completely pure.
- Run tests: `./gradlew test` to ensure successful execution.
