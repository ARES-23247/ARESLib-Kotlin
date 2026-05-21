# ARESLib-Kotlin Architectural & Code Quality Audit Report

This report presents a thorough static analysis and codebase health audit of the `ARESLib-Kotlin` high-performance robotics library. It evaluates compliance against five core architectural requirements (R1–R5), pinpointing key performance bottlenecks, math instability risks, and thread-blocking hardware integrations. It also provides robust, production-ready, zero-allocation remediations to eliminate these issues without altering core functionality.

---

## Executive Summary

`ARESLib-Kotlin` is designed as a functional, cross-platform (FTC and FRC) control library. Structurally, it excels in maintaining state purity, with 100% compliant, immutable data structures and side-effect-free reducers representing the robot state. 

However, under high-frequency execution (50Hz–100Hz), the current implementation introduces critical architectural risks:
1. **High GC Overhead (R2)**: Deep-copying history arrays and dynamically instantiating matrices inside EKF and VFH+ steering loops allocate thousands of objects per second, driving up garbage collection latency in JVM/ART runtimes.
2. **Replay Determinism Failures (R3)**: System clock leakage inside the Floodgate current sensor prevents simulation and sensory log reproducibility.
3. **Catastrophic Infinite Loop Risks (R4)**: Standard angular wrapping routines implemented with `while` loops will hang the main robot thread indefinitely when presented with infinite inputs (such as coordinate system failures).
4. **Main Thread Bus Blocking (R5)**: Direct synchronous I2C/UART reads are executed in hot motor setters and absolute encoder loops, stalling control cycle frequencies far below target budgets.

This audit details each of these findings along with concrete remediation blueprints.

---

## Section 1: State Immutability & Redux Purity (R1)

### Status: **PASS**

### Audit Findings
The Redux state management layer fully conforms to functional design constraints, ensuring complete segregation of state transitions and side-effects.

* **Audited Files**:
  * `core/src/main/kotlin/com/areslib/state/RobotState.kt`
  * `core/src/main/kotlin/com/areslib/state/SuperstructureState.kt`
  * `core/src/main/kotlin/com/areslib/state/PathState.kt`
  * Reducers under `core/src/main/kotlin/com/areslib/reducer/` (including `RootReducer.kt`, `DriveReducer.kt`, `SuperstructureReducer.kt`, and `CostmapReducer.kt`).

* **Compliance Evidence**:
  * **Immutable Fields**: Every state tree property is declared as a read-only Kotlin `val` and backs immutable Kotlin `data class` slices.
  * **Pure Functions**: Reducers never mutate state properties in-place. Instead, they produce a new state tree by utilizing the `.copy()` copy constructor to modify slices, returning the original state unchanged if an action is invalid or unhandled.
  * **Programmatic Enforcement**: This purity is strictly tested in `StateImmutabilityTier1Test.kt` using reflection. The test analyzes all compiled state classes to verify that all fields are marked `private final` at the JVM bytecode level.

---

## Section 2: Zero-GC Allocation in Hot-Paths (R2)

### Status: **FAIL (VIOLATIONS DETECTED)**

To maintain zero-allocation performance on resource-constrained platforms (REV Control Hub, RoboRIO), hot paths (such as `update()`, EKF filter steps, and obstacle detours) must not allocate temporary heap objects. The audit identified the following hot-path GC allocation sources:

### Violation 2.1: Lambda/Closure Allocation in Odometry Validation
* **File**: `core/src/main/kotlin/com/areslib/math/PoseEstimator.kt` (Line 131)
* **Code Snippet**:
  ```kotlin
  if (args.any { it.isNaN() || it.isInfinite() }) return state
  ```
* **Analysis**: The `any` extension function is called inside the 50Hz `addOdometryObservation` loop. Because it accepts a lambda parameter, it generates a `Function` object allocation on *every single tick*, violating the zero-GC budget.

### Violation 2.2: Retroactive History Buffer Deep Copying
* **File**: `core/src/main/kotlin/com/areslib/math/PoseEstimator.kt` (Lines 195, 335)
* **Code Snippets**:
  * Line 195: `val newHistory = state.history.deepCopy()` (inside `addOdometryObservation`)
  * Line 335: `val newHistory = state.history.deepCopy()` (inside `addVisionMeasurement`)
  * `HistoryBuffer.deepCopy()` implementation in `PoseEstimator.kt` (Lines 33–41):
    ```kotlin
    fun deepCopy(): HistoryBuffer {
        val newBuf = HistoryBuffer(capacity)
        for (i in 0 until capacity) {
            newBuf.entries[i] = entries[i].copy()
        }
        newBuf.head = head
        newBuf.count = count
        return newBuf
    }
    ```
* **Analysis**: At a 50Hz update rate, calling `deepCopy()` triggers a massive object allocation pattern. The routine allocates a new `HistoryBuffer` and copies all 50 `PoseHistoryEntry` items on every frame. This equals **2,500 object allocations per second** inside the primary thread, creating immense GC pressure and latency spikes.

### Violation 2.3: Lambda & Iterator Allocations in VFH+ Planner
* **File**: `core/src/main/kotlin/com/areslib/pathing/VFHPlanner.kt` (Line 145)
* **Code Snippet**:
  ```kotlin
  val hasUnpassedObstacles = obstacles.any { obs ->
      val obsProgress = obs.x * ux + obs.y * uy
      obsProgress + obs.radius + 0.15 > robotProgress
  }
  ```
* **Analysis**: During local obstacle avoidance detours, `computeDetourHeading` is invoked periodically. The `.any { ... }` function creates an implicit iterator wrapper and a lambda capture context on the heap, bypassing VFH's pre-allocated `valleyPool` optimization.

### Violation 2.4: Hot-Path Matrix & Covariance Object Creation
* **File**: `core/src/main/kotlin/com/areslib/math/Matrix3x3.kt` (Lines 12–16, 43–63) and `PoseEstimator.kt` (Lines 183, 193, 304, 323, 324)
* **Code Snippets**:
  * `Matrix3x3.kt`:
    ```kotlin
    operator fun plus(other: Matrix3x3) = Matrix3x3(...)
    operator fun times(scalar: Double) = Matrix3x3(...)
    ```
  * `PoseEstimator.kt`:
    * Line 183: `var currentQ = Q * tiltScale * slipScale`
    * Line 193: `val newCovariance = state.covariance + currentQ`
    * Line 304: `val S = baseEntry.covariance + R`
    * Line 323: `val I_minus_K = Matrix3x3.IDENTITY - K`
    * Line 324: `val updatedCovariance = I_minus_K * baseEntry.covariance`
* **Analysis**: Although `Matrix3x3` implements in-place mutators (`addInPlace`, `multiplyInPlace`, `setTo`), the EKF localization loop relies heavily on the standard binary operators `+` and `*`. This generates at least 5 to 10 new `Matrix3x3` and `Vector3` heap allocations per filter iteration, producing continuous garbage inside the high-frequency state-propagation loop.

---

## Section 3: Time-Determinism & Clock Purity (R3)

### Status: **FAIL (VIOLATIONS DETECTED)**

### Violation 3.1: Impure System Clock Leakage in Floodgate Current Sensor
* **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/FtcFloodgateCurrentSensor.kt` (Lines 21, 35, 113)
* **Code Snippet**:
  ```kotlin
  private var lastUpdateTime = System.nanoTime()
  // ...
  val currentTime = System.nanoTime()
  // ...
  lastUpdateTime = System.nanoTime()
  ```
* **Analysis**: The GoBilda power switch current telemetry sensor queries the hardware system clock via `System.nanoTime()` directly. This bypasses the project's mockable `RobotClock.currentTimeMillis()`, which is a critical design violation. Consequently:
  * Dynamic power integration and thermal accumulation ($I^2 \cdot t$) calculations use non-deterministic physical wall-clock time rather than simulated replay time.
  * Running sensory log replay tests on captured telemetry logs results in drifting calculations, completely breaking deterministic simulation and log playback.

---

## Section 4: Math Stability & Boundary Guard Audit (R4)

### Status: **FAIL (VIOLATIONS DETECTED)**

Robotic systems must survive numerical anomalies (division-by-zero, sensor dropouts, coordinate singularities) without crashing or locking up. The audit exposed several high-severity mathematical vulnerabilities:

### Violation 4.1: Catastrophic Infinite Loop Risk in Shoot-on-the-Move Error Normalization
* **File**: `frc-app/src/main/kotlin/com/areslib/frc/ARESRobot.kt` (Lines 177–180)
* **Code Snippet**:
  ```kotlin
  val headingError = shotResult.robotTargetHeadingRad - currentPose.heading.radians
  var wrappedError = headingError
  while (wrappedError > Math.PI) wrappedError -= 2.0 * Math.PI
  while (wrappedError < -Math.PI) wrappedError += 2.0 * Math.PI
  ```
* **Analysis**: If the shoot-on-the-move lookahead algorithm fails or faces a coordinate transformation singularity (e.g. dividing by zero when calculating target distance), `shotResult.robotTargetHeadingRad` resolves to `Double.POSITIVE_INFINITY` or `Double.NEGATIVE_INFINITY`.
  Because `Double.POSITIVE_INFINITY - X` equals `Double.POSITIVE_INFINITY`, subtracting $2\pi$ inside `while (wrappedError > Math.PI)` is a no-op. The loop condition remains `true` infinitely, causing an **irrecoverable thread lockup** of the main FRC robot execution thread. This results in instant communication loss, watchdog termination, and a safety disable on physical fields.

### Violation 4.2: Division by Zero in Deadband Scaling
* **File**: `core/src/main/kotlin/com/areslib/math/InputMath.kt` (Line 13)
* **Code Snippet**:
  ```kotlin
  fun applyDeadband(value: Double, deadband: Double): Double {
      if (abs(value) < deadband) return 0.0
      return (value - sign(value) * deadband) / (1.0 - deadband)
  }
  ```
* **Analysis**: If a pilot configures a deadband parameter of exactly `1.0` (or near `1.0` due to floating-point epsilon), the denominator `(1.0 - deadband)` evaluates to `0.0`. This produces an unhandled division-by-zero, driving downstream motor velocity command vectors directly to `Infinity` or `NaN`.

### Violation 4.3: Unbounded "While" Loop Angular Normalization
* **Files & Lines**:
  * `core/src/main/kotlin/com/areslib/reducer/CostmapReducer.kt` (Lines 54–56)
  * `core/src/main/kotlin/com/areslib/reducer/DriveReducer.kt` (Lines 44–46)
  * `core/src/main/kotlin/com/areslib/math/PoseEstimator.kt` (Lines 293–295)
  * `core/src/main/kotlin/com/areslib/pathing/VFHPlanner.kt` (Lines 135–136, 181–182)
* **Example Snippet (`PoseEstimator.kt:294-295`)**:
  ```kotlin
  while (headingDiff > Math.PI) headingDiff -= 2 * Math.PI
  while (headingDiff < -Math.PI) headingDiff += 2 * Math.PI
  ```
* **Analysis**: Relying on iterative subtraction loops for angular wrapping is highly dangerous. If sensor telemetry malfunctions or overflows, these loops can execute thousands of times per tick, blocking the control loop. In the worst-case scenario where the inputs are infinite, they lock up the entire framework.

---

## Section 5: Hardware Timeout & Thread Purity (R5)

### Status: **FAIL (VIOLATIONS DETECTED)**

Robotic control loops must execute at highly deterministic frequencies. Bypassing asynchronous/non-blocking reads by invoking synchronous hardware register transactions halts thread execution. The audit identified major blocking operations on the primary thread:

### Violation 5.1: Synchronous physical reads inside Motor Power Setter
* **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/FtcRevHubIO.kt` (Lines 40, 55)
* **Code Snippet**:
  ```kotlin
  override var power: Double
      get() = targetPower
      set(value) {
          targetPower = value
          val timeMs = com.areslib.util.RobotClock.currentTimeMillis()
          val currentVel = this.velocity // Synchronously calls DcMotorEx.getVelocity()
          // ...
          val amps = this.currentAmps // Synchronously calls DcMotorEx.getCurrent()
  ```
* **Analysis**: When setting motor power in the teleop or trajectory follow path, `FtcMotor` synchronously reads velocity and current from the physical REV Hub expansion controller over the physical I2C/UART buses. These synchronous reads require a 2–5ms transaction delay each. Across a 4-motor drivetrain, this introduces **16–40ms of blocking overhead per loop**, dragging the main thread's control loop frequency far below the 50Hz safety margin.

### Violation 5.2: Synchronous Blocking reads in OctoQuad Encoder IO
* **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/OctoquadIO.kt` (Lines 145, 148, 175)
* **Code Snippet**:
  ```kotlin
  override val velocity: Double
      get() = octoQuad.readEncoderVelocity(channel).toDouble()
  override val position: Double
      get() = octoQuad.readEncoderPosition(channel).toDouble()
  ```
* **Analysis**: Direct property accessors (`velocity` and `position`) invoke synchronous register reads `deviceClient.read(...)` on the main thread for the OctoQuad module, stalling the core high-frequency odometry loop.

### Violation 5.3: Duplicate Synchronous Read Bypassing Bulk Cache
* **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/SrsHubIO.kt` (Lines 262, 268)
* **Code Snippet**:
  ```kotlin
  val pulseUs = srsHub.readPwmPulseWidth(port).toDouble()
  ```
* **Analysis**: Although the `SrsHubDriver` implements an optimal, single 256-byte bulk repeated read window transaction (`deviceClient.read(0, 256)`) in its `update()` loop, the `SrsHubAbsolutePWMEncoder` bypasses this cached data. It calls `readPwmPulseWidth(port)`, executing an independent, synchronous I2C read transaction. This duplicates overhead and stalls the thread unnecessarily.

---

## Section 6: Actionable Remediations

To resolve these architectural issues without altering public API contracts, we propose the following production-grade, zero-allocation remediations:

### 1. In-Place Circular History Buffer (R2 Remedy)
To eliminate the 2,500 objects/sec allocated by `HistoryBuffer.deepCopy()`, replace the deep-copying array with an **in-place circular buffer array**. The EKF history rewind search and propagation should write directly into a pre-allocated history buffer, avoiding object instantiation:

```kotlin
// Pre-allocate a single, reusable history buffer in the PoseEstimatorState
class HistoryBuffer(private val capacity: Int = 50) : AbstractList<PoseHistoryEntry>() {
    private val entries = Array(capacity) { PoseHistoryEntry() }
    private var head = 0
    private var count = 0

    // Prevent deep copies. Instead of returning a new HistoryBuffer,
    // mutate values in-place or copy contents into a pre-allocated buffer
    fun copyInto(destination: HistoryBuffer) {
        destination.head = this.head
        destination.count = this.count
        for (i in 0 until capacity) {
            val src = this.entries[i]
            val dest = destination.entries[i]
            dest.timestampMs = src.timestampMs
            dest.pose.setTo(src.pose)          // In-place Pose2d assignment
            dest.covariance.setTo(src.covariance) // In-place Matrix3x3 assignment
        }
    }
}
```

### 2. Pre-Allocated Matrix Operations (R2 Remedy)
To avoid heap allocations for EKF covariance calculations in `PoseEstimator.kt`, leverage the in-place mutators `addInPlace`, `multiplyInPlace`, and `setTo` using pre-allocated matrix scratchpads inside the `PoseEstimator` object:

```kotlin
object PoseEstimator {
    // Thread-safe / Thread-confined pre-allocated scratchpad matrices
    private val scratchQ = Matrix3x3()
    private val scratchS = Matrix3x3()
    private val scratchK = Matrix3x3()
    private val scratchCov = Matrix3x3()

    fun addOdometryObservation(...) {
        // Instead of: val newCovariance = state.covariance + currentQ
        // Use in-place logic:
        scratchCov.setTo(state.covariance)
        scratchQ.setTo(Q)
        scratchQ.multiplyInPlace(tiltScale * slipScale)
        scratchCov.addInPlace(scratchQ)
        
        // Return a state.copy containing the updated pre-allocated or pooled matrices
    }
}
```

### 3. Safe Modulus Angular Normalization (R4 Remedy)
To prevent thread lockups caused by infinite inputs, replace all `while` loop normalizations with a non-iterative, closed-form modulus wrapping utility that handles infinity and NaN values safely:

```kotlin
object InputMath {
    /**
     * Safely wraps an angle in radians to the interval [-PI, PI].
     * Guarantees O(1) execution and instantly returns 0.0 on NaN or Infinity inputs.
     */
    fun wrapAngle(angleRad: Double): Double {
        if (angleRad.isNaN() || angleRad.isInfinite()) {
            return 0.0 // Return safe default instead of looping infinitely
        }
        val wrapped = (angleRad + Math.PI) % (2.0 * Math.PI)
        return if (wrapped < 0.0) wrapped + Math.PI else wrapped - Math.PI
    }
    
    /**
     * Division-by-zero guarded deadband scaling
     */
    fun applyDeadband(value: Double, deadband: Double): Double {
        if (abs(value) < deadband) return 0.0
        val denominator = 1.0 - deadband
        if (abs(denominator) < 1e-6) return 0.0 // Guard against division by zero
        return (value - sign(value) * deadband) / denominator
    }
}
```
All instances of heading wrapping in `ARESRobot.kt`, `PoseEstimator.kt`, `VFHPlanner.kt`, `DriveReducer.kt`, and `CostmapReducer.kt` must be refactored to call `InputMath.wrapAngle(angle)`.

### 4. Asynchronous Hardware Polling (R5 Remedy)
To eliminate synchronous I2C/UART bus transactions on the main thread, refactor the `FtcMotor` setter and encoders to read from asynchronously populated cache arrays during the hardware loop's `updateInputs` phase:

```kotlin
class FtcMotor(private val motor: DcMotorEx) : MotorIO {
    // Thread-safe cached inputs updated during the periodic sensor read phase
    private var cachedVelocity = 0.0
    private var cachedAmps = 0.0

    // Called asynchronously or at the very start of the control loop in a bulk batch
    fun updateInputs() {
        cachedVelocity = motor.velocity
        cachedAmps = motor.getCurrent(CurrentUnit.AMPS)
    }

    override var power: Double
        get() = targetPower
        set(value) {
            targetPower = value
            // Safe, non-blocking check using local cache instead of blocking physical reads
            val currentVel = this.cachedVelocity 
            val amps = this.cachedAmps
            
            // ... (Execute virtual breaker / stall detection checks instantly in 0.0ms)
            motor.power = computedPower
        }
}
```

### 5. Mockable RobotClock Integration (R3 Remedy)
To restore 100% deterministic simulation replay capability, replace `System.nanoTime()` calls in `FtcFloodgateCurrentSensor.kt` with `com.areslib.util.RobotClock.currentTimeMillis()`. Since the integration math uses seconds, scale the millisecond difference accordingly:

```kotlin
class FtcFloodgateCurrentSensor(private val analogInput: AnalogInput) {
    private var lastUpdateTimeMs = RobotClock.currentTimeMillis()
    private var isInitialized = false

    fun update() {
        val currentTimeMs = RobotClock.currentTimeMillis()
        val dtSeconds = if (isInitialized) {
            (currentTimeMs - lastUpdateTimeMs) / 1000.0
        } else {
            isInitialized = true
            0.0
        }
        lastUpdateTimeMs = currentTimeMs
        // ... (Execute filter math using deterministic dtSeconds)
    }
}
```

---

## Conclusion & Attestation

This audit reveals that while the functional Redux architecture (R1) of `ARESLib-Kotlin` is exceptionally well-structured, several high-frequency performance bottlenecks and stability risks remain in its mathematical and hardware-polling layers. 

Implementing the non-allocating circular buffers (R2), closed-form modular wrapping (R4), cached sensor polling (R5), and mockable clocks (R3) outlined in this report will enable the codebase to run with maximum efficiency and high-fidelity determinism.

**Audit conducted by:** Codebase Auditor and Reporter  
**Date:** May 21, 2026  
**Build Status:** Verification successful (`BUILD SUCCESSFUL in 25s`, all tests passing).
