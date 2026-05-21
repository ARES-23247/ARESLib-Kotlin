# ARESLib-Kotlin Codebase Audit & Compliance Report

This comprehensive audit evaluates the **ARESLib-Kotlin** robotics codebase against five critical architectural criteria (R1–R5), spanning State Immutability, Garbage Collection (GC) in Hot Paths, Clock Purity, Math Stability, and Hardware Thread Purity.

---

## 1. Observation

All tests passed successfully on the Windows host by executing the Gradle test wrappers. Specifically:
- `.\gradlew.bat test` completed successfully in **23 seconds** (92 actionable tasks: 17 executed, 75 up-to-date).
- `.\gradlew.bat :core:test` completed successfully in **17 seconds** (5 actionable tasks: 1 executed, 4 up-to-date).

However, during a thorough read-only static analysis of the source code, several major architectural violations were identified. The exact locations, code snippets, and specific findings are documented below:

### R1: State Immutability & Redux Purity (PASS)
- **Files Inspected**:
  - `core/src/main/kotlin/com/areslib/state/RobotState.kt`
  - `core/src/main/kotlin/com/areslib/state/SuperstructureState.kt`
  - `core/src/main/kotlin/com/areslib/state/PathState.kt`
  - All files under `core/src/main/kotlin/com/areslib/reducer/`
- **Finding**:
  - All state-tree fields are declared as read-only Kotlin properties (`val`) backing immutable data classes.
  - All reducers (`RootReducer.kt`, `DriveReducer.kt`, `SuperstructureReducer.kt`, etc.) are written as pure functions that return a new state slice using the `.copy()` function instead of in-place mutation.
  - Verification is enforced programmatically in `core/src/test/kotlin/com/areslib/e2e/tier1/state/StateImmutabilityTier1Test.kt` which uses Java reflection to verify that every field in the state classes is `private final`.

---

### R2: Zero-GC Allocation in Hot-Paths (VIOLATIONS DETECTED)

#### Violation 2.1: Lambda/Closure Allocation in Gyro/Odometry Validation
- **File**: `core/src/main/kotlin/com/areslib/math/PoseEstimator.kt` (Line 131)
- **Code Snippet**:
  ```kotlin
  if (args.any { it.isNaN() || it.isInfinite() }) return state
  ```
- **Analysis**: The `any` extension function takes a lambda parameter. When executed inside `addOdometryObservation` at 50Hz, this generates a lambda closure allocation on *every tick*, defeating zero-GC constraints in the high-frequency state propagation loop.

#### Violation 2.2: Retroactive History Rolling Copy (High Allocation Rate)
- **File**: `core/src/main/kotlin/com/areslib/math/PoseEstimator.kt` (Lines 195, 335)
- **Code Snippets**:
  - Line 195: `val newHistory = state.history.deepCopy()` (inside `addOdometryObservation`)
  - Line 335: `val newHistory = state.history.deepCopy()` (inside `addVisionMeasurement`)
  - `HistoryBuffer.deepCopy()` implementation (Lines 33–41):
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
- **Analysis**: The `deepCopy()` function instantiates a new `HistoryBuffer` and duplicates every single `PoseHistoryEntry` object via `.copy()`. Since the buffer capacity defaults to 50, updating odometry at 50Hz allocates **2,500 `PoseHistoryEntry` objects per second**. This places severe GC pressure on resources-constrained mobile platforms (Android Control Hub, RoboRIO).

#### Violation 2.3: Lambda Allocation in Vector Field Histogram (VFH+) Detour Planner
- **File**: `core/src/main/kotlin/com/areslib/pathing/VFHPlanner.kt` (Line 145)
- **Code Snippet**:
  ```kotlin
  val hasUnpassedObstacles = obstacles.any { obs ->
      val obsProgress = obs.x * ux + obs.y * uy
      obsProgress + obs.radius + 0.15 > robotProgress
  }
  ```
- **Analysis**: VFH local obstacle avoidance is processed periodically during active detours. The `.any { ... }` list operation dynamically allocates a Kotlin Function object (lambda) and an iterator wrapper for the `obstacles` list on every periodic loop invocation.

#### Violation 2.4: Hot-Path Covariance Matrix Arithmetic Allocations
- **File**: `core/src/main/kotlin/com/areslib/math/Matrix3x3.kt` (Lines 12–16, 43–63)
- **Code Snippet**:
  ```kotlin
  operator fun plus(other: Matrix3x3) = Matrix3x3(...)
  operator fun times(scalar: Double) = Matrix3x3(...)
  ```
- **Analysis**: While `Matrix3x3` contains `addInPlace`, `multiplyInPlace`, and `setTo` functions, the EKF updates in `PoseEstimator.kt` still invoke standard arithmetic operators:
  - Line 183: `var currentQ = Q * tiltScale * slipScale` (allocates a new `Matrix3x3` instance)
  - Line 193: `val newCovariance = state.covariance + currentQ` (allocates a new `Matrix3x3` instance)
  - Line 304: `val S = baseEntry.covariance + R` (allocates a new `Matrix3x3` instance)
  - Line 323: `val I_minus_K = Matrix3x3.IDENTITY - K` (allocates a new `Matrix3x3` instance)
  - Line 324: `val updatedCovariance = I_minus_K * baseEntry.covariance` (allocates a new `Matrix3x3` instance)
  These allocations in 50Hz filter steps trigger frequent garbage collection in JVM runtimes.

---

### R3: Time-Determinism & Clock Purity (VIOLATIONS DETECTED)

#### Violation 3.1: Impure System Clock in goBILDA Floodgate Current Sensor
- **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/FtcFloodgateCurrentSensor.kt` (Lines 21, 35, 113)
- **Code Snippet**:
  ```kotlin
  private var lastUpdateTime = System.nanoTime()
  // ...
  val currentTime = System.nanoTime()
  // ...
  lastUpdateTime = System.nanoTime()
  ```
- **Analysis**: The GoBilda power switch telemetric current sensor queries `System.nanoTime()` directly rather than using the library's `RobotClock.currentTimeMillis()`. This violates clock purity, meaning its low-pass filter and integration calculations (`dtSeconds`) are non-deterministic. During sensory replay test suites, the power calculation runs in physical time rather than simulated replay time, invalidating reproducibility.

---

### R4: Math Stability & Boundary Guard Audit (VIOLATIONS DETECTED)

#### Violation 4.1: Catastrophic Infinite Loop Risk in Shoot-on-the-Move Error Wrapping
- **File**: `frc-app/src/main/kotlin/com/areslib/frc/ARESRobot.kt` (Lines 177–180)
- **Code Snippet**:
  ```kotlin
  val headingError = shotResult.robotTargetHeadingRad - currentPose.heading.radians
  var wrappedError = headingError
  while (wrappedError > Math.PI) wrappedError -= 2.0 * Math.PI
  while (wrappedError < -Math.PI) wrappedError += 2.0 * Math.PI
  ```
- **Analysis**: If `shotResult.robotTargetHeadingRad` is resolved to `Double.POSITIVE_INFINITY` or `Double.NEGATIVE_INFINITY` (e.g. from an unhandled division-by-zero or matrix singularity during coordinate transformation), `wrappedError` will be set to infinity. Because `Double.POSITIVE_INFINITY - X` remains `Double.POSITIVE_INFINITY`, the `while (wrappedError > Math.PI)` loop condition remains true infinitely. This will cause an **irrecoverable infinite loop**, locking up the main FRC robot execution thread, causing instant communication loss and safety disablement.

#### Violation 4.2: Division by Zero Risk in Joystick Deadbands
- **File**: `core/src/main/kotlin/com/areslib/math/InputMath.kt` (Line 13)
- **Code Snippet**:
  ```kotlin
  fun applyDeadband(value: Double, deadband: Double): Double {
      if (abs(value) < deadband) return 0.0
      return (value - sign(value) * deadband) / (1.0 - deadband)
  }
  ```
- **Analysis**: If the configured deadband is exactly `1.0` (or `1.0` within floating-point epsilon), the denominator `(1.0 - deadband)` evaluates to `0.0`. This produces an unhandled **Division by Zero**, driving motor control signals to `Infinity` or `NaN`.

#### Violation 4.3: Dangerous While-Loop Angular Wrapping Patterns
- **Files**:
  - `core/src/main/kotlin/com/areslib/reducer/CostmapReducer.kt` (Lines 54–56)
  - `core/src/main/kotlin/com/areslib/reducer/DriveReducer.kt` (Lines 44–46)
  - `core/src/main/kotlin/com/areslib/math/PoseEstimator.kt` (Lines 293–295)
  - `core/src/main/kotlin/com/areslib/pathing/VFHPlanner.kt` (Lines 135–136, 181–182)
- **Code Snippet Example (PoseEstimator.kt:294–295)**:
  ```kotlin
  while (headingDiff > Math.PI) headingDiff -= 2 * Math.PI
  while (headingDiff < -Math.PI) headingDiff += 2 * Math.PI
  ```
- **Analysis**: Utilizing `while` loops for unbounded angular normalization exposes the codebase to infinite loops if any sensor telemetry overflows or introduces `Infinity`. Furthermore, if the input angle is extremely large, the loop executes repeatedly in a blocking manner, reducing control loop frequency.

---

## 5. Hardware Timeout & Thread Purity (VIOLATIONS DETECTED)

#### Violation 5.1: Synchronous I2C Read inside Hot Loop Motor Setter
- **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/FtcRevHubIO.kt` (Lines 40, 55)
- **Code Snippet**:
  ```kotlin
  override var power: Double
      get() = targetPower
      set(value) {
          targetPower = value
          val timeMs = com.areslib.util.RobotClock.currentTimeMillis()
          val currentVel = this.velocity // Calls DcMotorEx.getVelocity()
          // ...
          val amps = this.currentAmps // Calls DcMotorEx.getCurrent(...)
  ```
- **Analysis**: When setting motor power in the telemetry/drive hot path, `FtcMotor` synchronously invokes motor velocity and motor current readings from the physical REV Hub expansion controllers. These reads require synchronous I2C/UART bus transactions, which take ~2–5ms each in the FTC SDK. This blocks the main thread of the OpMode, completely defeating the threaded hardware polling architecture used elsewhere.

#### Violation 5.2: Synchronous Blocking I2C Reads in Quadrature Encoders
- **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/OctoquadIO.kt` (Lines 145, 148, 175)
- **Code Snippet**:
  ```kotlin
  override val velocity: Double
      get() = octoQuad.readEncoderVelocity(channel).toDouble()
  override val position: Double
      get() = octoQuad.readEncoderPosition(channel).toDouble()
  ```
- **Analysis**: Accessing `velocity` or `position` properties on `OctoQuadEncoderIO` triggers an instantaneous synchronous bus transaction `deviceClient.read(...)` on the main thread, causing severe blocking and cycle times.

#### Violation 5.3: Duplicate Synchronous Read Bypassing Bulk Cache
- **File**: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/SrsHubIO.kt` (Lines 262, 268)
- **Code Snippet**:
  ```kotlin
  val pulseUs = srsHub.readPwmPulseWidth(port).toDouble()
  ```
- **Analysis**: Even though the `SrsHubDriver` correctly reads the entire 256-byte register block in a single bulk transaction (`deviceClient.read(0, 256)`), `SrsHubAbsolutePWMEncoder` bypasses this cached data and calls `readPwmPulseWidth(port)` (which executes a separate, synchronous I2C transaction `deviceClient.read(24 + port * 2, 2)`). This duplicates I2C overhead and blocks the main thread thread needlessly.

---

## 2. Logic Chain

The reasoning linking raw observations to the architectural audit conclusions follows these steps:

1. **State Purity Verification**: Reflection tests in `StateImmutabilityTier1Test.kt` programmatically prove the state classes are fully immutable, and inspections of all reducer files show that they never modify properties in-place, indicating R1 compliance is fully met.
2. **GC Pressure Identification**: Android ART runtime garbage collection latency spikes are triggered by high object-allocation rates. The `PoseEstimator.addOdometryObservation` method executes on a 20ms periodic cycle (50Hz).
   - *Logic*: Since `PoseEstimator` deep-copies the `HistoryBuffer` on every call, and `deepCopy` invokes `.copy()` on all 50 `PoseHistoryEntry` items, it yields `50 items * 50Hz = 2,500 allocations/sec`. This high rate will force the GC to run periodically, introducing thread pauses that degrade high-frequency control loops.
   - *Logic*: Lambda closures that capture local context (e.g. `.any` and `.filter` in EKF loops and detour steering loops) instantiate JVM `Function` objects on every tick, adding to this GC overhead.
3. **Replay Determinism**: A fundamental requirement for offline sensory log replay is that time must be fully mockable.
   - *Logic*: `RobotClock` allows replacing system clock readings with injected test clock times. Since `FtcFloodgateCurrentSensor` bypasses `RobotClock` and calls `System.nanoTime()` directly, its internal `dtSeconds` becomes completely un-reproducible during tests, failing R3.
4. **Infinite Loop Analysis**: Main thread execution lockup is caused by non-terminating loop conditions.
   - *Logic*: In `ARESRobot.kt:177`, `wrappedError` is processed via `while (wrappedError > Math.PI) wrappedError -= 2.0 * Math.PI`. If heading calculations fail and return `POSITIVE_INFINITY`, `Double.POSITIVE_INFINITY - X` remains `POSITIVE_INFINITY`. The loop's condition will always remain true, hanging the entire robot control thread. Modulus mathematical functions should be used instead of `while` loops to normalize angles safely.
5. **Bus Thread Blocking**: I2C and UART communications with external hardware devices are slow (millisecond-scale) compared to CPU cycles.
   - *Logic*: Reading motor velocities and currents over physical registers during a synchronous power assignment (`FtcMotor.power = value`) halts the thread execution until the hub responds. If multiple motors are updated, this blocking behavior multiplies, drastically reducing the control loop rate below the 50Hz target.

---

## 3. Caveats

- **FRC Physical Testing**: Physical testing was restricted to simulating FRC hardware (`isSimulation = true` fallback) because the audit is conducted in a local read-only development workspace without connected FRC CAN-bus peripherals or RoboRIOs.
- **FTC Hardware Absence**: We simulated FTC OpModes using `ftc-mocks` project since no physical REV Expansion Hubs or GoBilda Pinpoint computers were physically present.
- **Write Permission Constraints**: The investigation is strictly read-only. No code modifications have been made to the target source directories.

---

## 4. Conclusion

While the ARESLib-Kotlin framework compiles perfectly and features excellent architectural purity in its state-immutability model (R1), it contains critical bottlenecks and safety risks:
1. **Garbage Collection (R2)**: EKF history buffer copying and matrix operations generate high GC pressure (~2500+ obj/sec) that can cause stutter in 50Hz/100Hz control loops.
2. **Clock Impurity (R3)**: `FtcFloodgateCurrentSensor` uses an impure clock, breaking simulation replay determinism.
3. **Math Stability (R4)**: Catastrophic infinite loop risks exist in angle wrapping (`while` loops with infinite inputs) and division-by-zero risks exist in deadband curves.
4. **Hardware Blocking (R5)**: Synchronous, blocking I2C transactions inside motor setters and absolute encoders bypass bulk reading features, stalling the main control thread.

### Actionable Remediations:
- **R2 Fix**: Implement an in-place circular history buffer array that overwrites values directly without duplicating or copying `PoseHistoryEntry` objects or instantiating a new `HistoryBuffer`.
- **R3 Fix**: Replace `System.nanoTime()` in `FtcFloodgateCurrentSensor.kt` with `com.areslib.util.RobotClock.currentTimeMillis()`.
- **R4 Fix**: Rewrite all angular wrapping and normalization routines to use `com.areslib.math.InputMath` or a safe mathematical modulus function:
  ```kotlin
  fun wrapAngle(angleRad: Double): Double {
      val wrapped = (angleRad + Math.PI) % (2.0 * Math.PI)
      return if (wrapped < 0.0) wrapped + Math.PI else wrapped - Math.PI
  }
  ```
- **R5 Fix**: Restructure `FtcMotor` to fetch cached readings populated asynchronously in `updateInputs()` instead of calling `DcMotorEx.getVelocity()` inside the `power` setter. Modify `SrsHubAbsolutePWMEncoder` to read cached PWM values directly from the driver's bulk parsing registers instead of executing separate I2C reads.

---

## 5. Verification Method

To verify these findings and reproduce the behaviors:

1. **Verify Compilation and Test Execution**:
   Run the following commands in the root of the project to ensure tests are healthy:
   ```powershell
   .\gradlew.bat test
   .\gradlew.bat :core:test
   ```
2. **Inspect the Violations**:
   - Open `core/src/main/kotlin/com/areslib/math/PoseEstimator.kt` and inspect lines 131 and 195.
   - Open `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/FtcFloodgateCurrentSensor.kt` and inspect lines 21, 35, 113.
   - Open `frc-app/src/main/kotlin/com/areslib/frc/ARESRobot.kt` and inspect lines 177–180.
   - Open `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/FtcRevHubIO.kt` and inspect lines 40–56.
   - Open `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/SrsHubIO.kt` and inspect lines 261–272.
3. **Verify Math Infinite Loop**:
   A test proving Violation 4.1 can be written by feeding `Double.POSITIVE_INFINITY` as heading inputs to the wrapping logic, verifying that the loop hangs indefinitely.
