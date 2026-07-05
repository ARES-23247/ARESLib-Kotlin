# ARESLib-Kotlin: AI & Developer Guidelines

This document serves as the absolute source of truth for repository structure, building, testing, and coding standards. AI agents and developers must strictly adhere to these directives.

---

## 1. Project Overview & Architecture

`ARESLib-Kotlin` is a high-performance, functional, cross-platform (FTC and FRC) robotics library. The codebase is designed around two core principles: **Immutable State Representation** (Redux-style flow) and **Decoupled Hardware Interfaces** (IO Layer pattern).

```mermaid
graph TD
    A[Driver Input / Gamepads] -->|Joystick Drive Intent| B[Mecanum / Swerve Facades]
    B -->|Dispatch Action| C[Store / State Reducer]
    C -->|Calculate Kinematics| D[Kinematics & Control Controllers]
    D -->|Voltage Command| E[Hardware IO Layer]
    E -->|Write| F[Physical Motors / Mocks]
    F -->|Read Sensors| G[Pinpoint / Gyro / Vision IO]
    G -->|Dispatch Observation| C
```

### Core Architecture Constraints:
1. **Redux Store Architecture**: 
   * **State**: The `RobotState` and its sub-states (`DriveState`, `SuperstructureState`, etc.) are 100% immutable data classes.
   * **Actions**: All state updates occur by dispatching `RobotAction` objects.
   * **Reducers**: State transitions are handled exclusively through pure, deterministic reducer functions (e.g., `rootReducer`).
2. **Unified Simulation Clock**:
   * **CRITICAL**: Never call `System.currentTimeMillis()` or `System.nanoTime()` inside library code.
   * Always use `com.areslib.util.RobotClock.currentTimeMillis()` to ensure that simulation logs and replay runs are perfectly deterministic and free from wall-clock drift.
3. **Android/RoboRIO GC Allocation Budget**:
   * Drivetrain update cycles, state-space controller loops, and pathfinders execute at high frequencies (50Hz - 100Hz).
   * **CRITICAL**: Object allocations are prohibited inside hot paths (e.g. `update()`, trajectory sampling, VFH steering loops). 
   * Always use pre-allocated buffers, primitive types, and object pools (like `Valley` pools in `VFHPlanner`) to maintain a zero-allocation footprint.
4. **Decoupled Hardware IO Layer**:
   * All hardware interactions are abstracted through thin IO interfaces (e.g. `MecanumHardwareIO`, `PinpointIO`).
   * The actual implementation is split between physical SDK implementations (`ftc-hardware/`) and robust mock components (`ftc-mocks/`), enabling 100% offline desktop-level simulation.

---

## 2. Directory Structure

* **`core/`**: Pure mathematical, planning, and control logic. Fully decoupled from FRC and FTC SDKs.
  * `src/main/kotlin/com/areslib/state/`: Immutable Redux state definitions.
  * `src/main/kotlin/com/areslib/control/`: DARE-converged LQR controllers, Kalman observers, gravity feedforwards.
  * `src/main/kotlin/com/areslib/math/`: EKF localization, Mahalanobis outlier filtering, geometry wrappers.
  * `src/main/kotlin/com/areslib/pathing/`: Theta* any-angle pathfinders, costmap inflation, jerk-limited S-curve generators, and VFH+.
  * `src/main/kotlin/com/areslib/subsystem/`: Subsystem facades and Ares robot definitions.
* **`ftc-hardware/`**: FTC-specific hardware wrapping, GoBilda Pinpoint and Limelight integration, and the student-facing Mecanum robot facade.
* **`ftc-mocks/`**: Stubbed, light implementation of Qualcomm and external FTC APIs, enabling core compilation and desktop test executions without Android hardware.
* **`frc-app/`**: FRC-specific kinematics, Swerve Facades, and WPILib adapters.
* **`simulator/`**: Dynamic physics simulator and dynamic visualizers.

---

## 3. Build & Test Commands

Always default to using the local Gradle wrapper (`gradlew.bat` on Windows, `./gradlew` on Linux/WSL2).

### Standard Development Commands:
* **Compile Kotlin Code**:
  ```powershell
  .\gradlew.bat compileKotlin compileTestKotlin
  ```
* **Run Entire Test Suite**:
  ```powershell
  .\gradlew.bat test
  ```
* **Run Specific Module Tests**:
  ```powershell
  .\gradlew.bat :core:test
  .\gradlew.bat :ftc-hardware:test
  ```
* **Publish to Local Maven Repository (Transitive Dependencies Packaged)**:
  ```powershell
  .\gradlew.bat publishToMavenLocal
  ```
* **Clean Build Cache**:
  ```powershell
  .\gradlew.bat clean build
  ```

---

## 4. Coding Conventions

1. **Kotlin Features**:
   * Leverage Kotlin DSLs for student configuration: `aresRobot { ... }` and `ftcMecanumRobot(hardwareMap) { ... }`.
   * Use trailing lambdas and functional programming idioms.
2. **KDoc API Documentation**:
   * All public classes, parameters, facades, and mathematical algorithms must be documented using descriptive inline KDoc formatting.
   * Document specific math equations, coordinate directions, positive/negative rotations, and expected physical units (meters, radians, seconds).
3. **Redux Reducer Safety**:
   * Reducer logic must be pure. No side-effects, I/O calls, or clock calls inside reducers.
   * Use `.copy()` on state data classes to transition values.

---

## 5. Cloud Telemetry & Networking Guidelines

1. **ARES-Analytics Gateway Architecture**:
   * The backend gateway (`aresfirst-portal`) runs on Ktor in Google Cloud Run. It accepts high-throughput payloads (Parquet) via secure GCS Signed URLs.
   * The gateway **does not** accept raw `.jsonl` files directly from robots.
2. **Offline-First Robot Operations**:
   * FTC Control Hubs and FRC RoboRIOs operate without internet access during competition matches.
   * Log uploading must follow the **Desktop Pull Architecture**:
     * The `LogManagerServer` (NanoHTTPD) running on port `5002` must expose local endpoints (like `/api/download`) to serve raw log files locally.
     * The ARES-Analytics desktop application pulls these logs to the driver station laptop, parses them into SQLite, and then the laptop handles the delta-sync and GCS uploads to the cloud.
