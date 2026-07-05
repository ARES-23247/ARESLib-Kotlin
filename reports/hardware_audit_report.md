# ARESLib-Kotlin High-Fidelity Audit Report

**Date:** July 4, 2026
**Auditor Name:** Antigravity (Lead Code Reviewer)
**Scope:** `ARESLib-Kotlin/ftc-hardware` & `ARESLib-Kotlin/ftc-mocks` Modules

---

## 📊 Summary Scorecard

| Pillar | Grade | Critical Item Summary |
| :--- | :---: | :--- |
| **P1: State Immutability** | **A** | Measurements and inputs are correctly passed as pure data classes. |
| **P2: Zero-GC Allocation** | **F** | Massive object allocations in `FtcLimelightIO` and `ColorSensor` hot paths. |
| **P3: Time-Determinism** | **A** | Perfect usage of virtual `RobotClock` across all hardware layers. |
| **P4: Math Stability** | **A** | Excellent use of closed-form angle wrapping and division-by-zero guards. |
| **P5: Thread Purity** | **B-** | Background I2C daemons are excellent, but `FtcLimelightIO` blocks the main thread on reconnect. |
| **P6: API Design & KDoc** | **C** | Good Limelight docs, but missing coordinate space definitions for Pinpoint. |
| **P9: Code Portability** | **A** | Excellent `PinpointDriverProxy` abstraction in `ftc-mocks`. |
| **P12: System Robustness** | **B** | Try-catch guards are solid, but Limelight restart logic needs safety refinement. |

*(Note: Pillars 7, 8, 10, 11 were not primarily impacted or evaluated in this hardware IO-specific scope).*

---

## 📋 Detailed Evaluation

### 1. State Immutability & Redux Purity (R1) 🔒
✅ **Strengths:** 
- `FtcLimelightIO` and `PinpointIO` correctly bundle outputs into read-only structures like `VisionMeasurement` and `RobotAction.PoseUpdate`.

### 2. Zero-GC Allocation in Hot-Paths (R2) ⚡
⚠️ **Findings:**
- **Severe GC Pressure in Vision:** `FtcLimelightIO.updateInputs()` is executed at high frequencies. In lines 44-104, it allocates `Translation3d`, `Rotation3d`, and `Pose3d` instances multiple times per loop iteration. It also instantiates a new `ArrayList<VisionMeasurement>` and `VisionMeasurement` objects constantly.
- **Color Sensor Allocation:** In `FtcRevColorSensorV3` and `FtcColorSensor`, the background daemon continuously calls `normalizedSensor?.normalizedColors`. The FTC SDK instantiates a new `NormalizedColors` object on every call. Even though it runs on a background thread, this churn triggers Android Garbage Collection, which pauses all threads (including the primary control loop).

### 3. Time-Determinism & Clock Purity (R3) ⏰
✅ **Strengths:** 
- Zero usages of `System.currentTimeMillis()`. `PinpointIO`, `PinpointOdometryIO`, and `FtcLimelightIO` strictly utilize `com.areslib.util.RobotClock.currentTimeMillis()`.

### 4. Math Stability & Boundary Guards (R4) 🎛️
✅ **Strengths:** 
- `PinpointIO` avoids `while` loops for angles by utilizing `InputMath.wrapAngle()` (O(1) closed-form).
- `FtcRevColorSensorV3` includes a safe `if (sum < 0.1)` check to prevent division-by-zero when calculating normalized RGB values.

### 5. Hardware Timeout & Thread Purity (R5) 🔌
✅ **Strengths:** 
- **I2C Isolation:** `FtcDistanceSensor`, `FtcColorSensor`, `FtcRevColorSensorV3`, and `PinpointIO` correctly spawn dedicated background threads (e.g., `ARES-DistanceSensor-Thread`). The synchronous I2C blocking reads (`sensor.getDistance`, `sensor.red()`, `driver.update()`) run entirely off the primary control thread, caching values securely behind a `synchronized(lock)`.

⚠️ **Findings:**
- **Main Thread Blocking on Limelight Reconnect:** In `FtcLimelightIO.updateInputs()`, if the sensor fails, the `catch` block invokes `limelight.start()` (Line 133). Since `updateInputs()` is called by the primary control thread, `limelight.start()` (a blocking hardware initialization call) will stall the primary control loop, leading to watchdog timeouts.

### 6. API Design, KDoc, and Coordinate Transformations 📝
✅ **Strengths:** 
- **Limelight Y-up vs FTC Z-up (Target Space):** `FtcLimelightIO` correctly implements the Limelight target-space mappings exactly as dictated by `AGENTS.md`. The translation and Euler rotation axes (`rotation.y` for heading) preserve the Limelight orientation instead of transforming into WPILib space. The KDoc accurately warns consumers to negate `rotation.y`.
- **Limelight Botpose Transform:** The transformation from FTC Field (`+Y forward, +X right`) to WPILib (`+X forward, +Y left`) using `(x = pos.y, y = -pos.x)` and the `-90` deg Z rotation is mathematically sound.

⚠️ **Findings:**
- **Pinpoint Coordinate Ambiguity:** `PinpointIO` reads `getPosX()` and `getPosY()` but performs zero coordinate transformation before injecting them into the `PoseUpdate`. The FTC field standard is `X-right`, `Y-forward`, whereas WPILib is `X-forward`, `Y-left`. If the GoBilda driver returns FTC field coordinates, `PinpointIO` is feeding incorrect axes to the WPILib-based odometry stack.

---

## 🔍 Findings Register

| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| **TAG-F01** | **[HIGH]** | **Massive GC Allocations:** `Pose3d`, `Translation3d`, and `ArrayList` are allocated continuously inside `updateInputs()`. | `FtcLimelightIO.kt:44-104` |
| **TAG-F02** | **[HIGH]** | **Primary Thread Block:** `limelight.start()` is called synchronously inside `updateInputs()` upon failure. | `FtcLimelightIO.kt:133` |
| **TAG-F03** | **[MED]** | **Background GC Churn:** `normalizedSensor?.normalizedColors` allocates memory every 20ms. | `FtcRevColorSensorV3.kt:52` & `FtcColorSensor.kt:42` |
| **TAG-F04** | **[MED]** | **Coordinate Transform Missing:** `PinpointIO` passes raw coordinates without verifying or applying FTC to WPILib axis transforms. | `PinpointIO.kt:28-35` |

---

## 🗺️ Roadmap to Compliance

### 🔴 Must Fix (Prior to Next Test Run)
1. **Refactor `FtcLimelightIO` Allocations:** Move `Translation3d`, `Rotation3d`, and `Pose3d` instantiations to pre-allocated mutable scratchpad variables inside the class, or use primitive double arrays for the hardware translation layers. Eliminate the `ArrayList` allocation.
2. **Move Limelight Restart:** Wrap `limelight.start()` inside an asynchronous background thread or Coroutine in the `catch` block to prevent stalling the main thread during a camera crash.

### 🟡 Should Fix (During Refactoring Phase)
1. **Pinpoint Axis Verification:** Explicitly document in KDoc whether `PinpointIO` expects the physical module to be configured for FTC or WPILib coordinates. If FTC, implement the `x = y, y = -x` transform used in the Limelight IO.
2. **Eliminate FTC SDK Color Object:** Stop using `.normalizedColors`. Construct the normalization manually from raw `red()`, `green()`, `blue()`, and `alpha()` primitives to avoid the internal object instantiation.

### 🟢 Backlog
1. Implement a unified `BackgroundHardwareRunner` to manage all these background daemon threads uniformly, reducing boilerplate across sensor classes.
