# Requirements - Milestone v2.8: Deterministic Input Replay & "What-If" Ghost Simulation

Active requirements for Milestone v2.8.

## Active Requirements

### Category: Subsystem IO Design
* [ ] **IO-01 (Unified Subsystem IO Interfaces)**: Define platform-agnostic `SwerveIO`, `OdometryIO`, and `VisionIO` interfaces in `:core`. Each must declare a serializable nested data class `Inputs` holding raw, unfiltered sensor data.
* [ ] **IO-02 (Mock/Simulation Implementations)**: Implement mock and simulation variants of these IO interfaces that run identically on both pure JVM testing and REV Control Hubs.

### Category: Raw Sensory Logging
* [ ] **REC-01 (InputLogger Thread-Safety)**: Create a high-performance, asynchronous `InputLogger` that serializes and flushes raw `Inputs` structs to a JSONL file without blocking the main control loop.
* [ ] **REC-02 (Low Garbage Allocation)**: Design the serialization pipeline to reuse buffers or object pools, minimizing garbage collection allocations to guarantee zero loop-time jitter.

### Category: Replay Engine
* [ ] **REP-01 (Clock Mocking Interceptor)**: Implement a mockable `RobotClock` system allowing the replay engine to inject historical log timestamps, ensuring identical integration math.
* [ ] **REP-02 (Dual-State Reducer Runner)**: Implement an offline replay runner that executes log data through the root reducer and EKF, simultaneously generating `LoggedRobotState` and `ReplayedRobotState` traces.

### Category: Student GUI Dashboard
* [ ] **GUI-01 (Compose Desktop Architecture)**: Set up a `:tools:replay-gui` Compose Multiplatform module runnable with a single Gradle task (`./gradlew :tools:replay-gui:run`).
* [ ] **GUI-02 (Interactive Sliders & Tuning)**: Provide visual sliders for Kalman Filter vision trust (standard deviation bounds), collision lockouts, and loop gains. Adjusting a slider must trigger a live, sub-millisecond local replay.
* [ ] **GUI-03 (2D Field Canvas Visualizer)**: Renders a high-fidelity 2D canvas of the field showing the original logged trajectory vs. the new "Ghost" trajectory dynamically.
* [ ] **GUI-04 (Grid Sweep Auto-Tuner)**: Integrate a one-click optimizer button that sweeps parameters over the loaded log to find and set the mathematically optimal values.

### Category: Diagnostics & Telemetry
* [ ] **TEL-01 (AdvantageScope Dual-Pose NT4)**: Stream separate NT4 pathways (`/Odom/Real` and `/Odom/Ghost`) simultaneously to render overlapping robots in AdvantageScope's 3D visualizer.

---

## Traceability

| Requirement ID | Description | Mapped Phase | Status |
|----------------|-------------|--------------|--------|
| **IO-01**     | Unified Subsystem IO Interfaces | Phase TBD    | Planned|
| **IO-02**     | Mock/Simulation Implementations | Phase TBD    | Planned|
| **REC-01**    | InputLogger Thread-Safety | Phase TBD    | Planned|
| **REC-02**    | Low Garbage Allocation | Phase TBD    | Planned|
| **REP-01**    | Clock Mocking Interceptor | Phase TBD    | Planned|
| **REP-02**    | Dual-State Reducer Runner | Phase TBD    | Planned|
| **GUI-01**    | Compose Desktop Architecture | Phase TBD    | Planned|
| **GUI-02**    | Interactive Sliders & Tuning | Phase TBD    | Planned|
| **GUI-03**    | 2D Field Canvas Visualizer | Phase TBD    | Planned|
| **GUI-04**    | Grid Sweep Auto-Tuner | Phase TBD    | Planned|
| **TEL-01**    | AdvantageScope Dual-Pose NT4 | Phase TBD    | Planned|

---

## Future Requirements (Deferred)
- None.

---

## Out of Scope
- Direct bytecode manipulation or custom Kotlin compiler plugins (ASM / KAPT) — we will use native Kotlin serialization to maintain zero build-time overhead.
