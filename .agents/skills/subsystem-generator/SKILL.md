---
name: subsystem-generator
description: Generates boilerplate files for a new robot subsystem using the Redux state and decoupled IO layers pattern in ARESLib-Kotlin
---

# Subsystem Generation Protocol

Use this skill when the developer requests to create or add a new subsystem (e.g. Climber, Intake, Shooter, Arm, Claw, Turret). You must generate the complete 6-file suite adhering strictly to the architecture guidelines:

## Architectural Requirements
1. **Core Package (`com.areslib.state`)**:
   - `[SubsystemName]State.kt`: Immutable data class containing configuration and current state metrics.
   - `[SubsystemName]Action.kt`: Sealed class of transition intents (e.g. `SetTarget`, `UpdateSensors`).
   - `[SubsystemName]Reducer.kt`: Pure, side-effect-free reducer mapping actions to state updates via `.copy()`.
2. **Hardware Abstraction (`com.areslib.hardware`)**:
   - `[SubsystemName]IO.kt`: A thin, clean interface extending `AutoCloseable` or `SubsystemIO` with `writeTarget(...)` and `read()` methods.
3. **FTC Implementation (`com.areslib.ftc`)**:
   - `Ftc[SubsystemName]IO.kt`: Wraps FTC SDK DcMotorEx/Servos/Sensors. Uses throttled telemetry reads for current draw to satisfy the Zero-GC budget.
4. **Mock Implementation (`com.areslib.ftc`)**:
   - `Mock[SubsystemName]IO.kt`: In-memory physics simulation integrating motor voltage/power to yield mock position, velocity, current draw, and limit switch transitions.
5. **Controller Layer (`com.areslib.control`)**:
   - `[SubsystemName]Controller.kt`: Periodic loop reading target inputs, applying closed-loop (PID/feedforward) output voltage calculations, writing to `IO`, and dispatching telemetry updates.

## Memory Budget Invariant
- **Zero Heap Allocations**: All update loops must recycle pre-allocated observation dataclasses (like `ClimberInputData`). Never instantiate new buffers or wrappers inside the hot path.

## Auto-Registration Template
Remind the developer to register the new subsystem state in the root state constructor:
```kotlin
val state = RobotState(
    superstructure = SuperstructureState(
        states = mapOf("[subsystem_key]" to [SubsystemName]State())
    )
)
```
