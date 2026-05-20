# Handoff Report

## Observation
- **PIDController.kt**: 
  - `calculate()` uses `dtSeconds > 0` checks, but does not validate against anomalous values like `NaN` or `Infinity`.
  - Integral accumulation (`totalError += error * dtSeconds` at line 64) has no maximum bound (integral windup vulnerability).
  - The calculated return value (`p * error + i * totalError + d * velocityError` at line 70) lacks output clamping limits.
- **LQRController.kt**: 
  - **GC Allocations**: Every call to `calculate()` allocates multiple `Matrix` objects and their backing `DoubleArray`s. For instance: `xHat.subtract(xRefMat)` (line 127), `K.multiply(...)` (line 128), `A.multiply(xHat)` (line 149), and several others. Returning `saturatedU.getColumn(0)` allocates a new `DoubleArray` on line 155.
  - **Boxed primitive**: `var maxUChangePerSec: Double? = null` (line 39) introduces primitive boxing/unboxing GC overhead.
  - **Bounds & Singularity**: `dtSeconds` is not verified to be `> 0`. If `dtSeconds < 0`, `maxChange` becomes negative and `coerceIn(-maxChange, maxChange)` (line 139) will throw an exception. Input arrays (`y` and `xRef`) lack `NaN`/`Infinity` checks.
- **GravityFeedforward.kt**:
  - Functions directly perform calculations without verifying if `kG` or `angleRadians` are finite (i.e. not `NaN` or `Infinity`).

## Logic Chain
- **Numerical Bounds**: Without strict `isFinite()` checks, corrupted sensor data (`NaN` or `Infinity`) will propagate through the controller's internal state (like `totalError` or `xHat`), permanently bricking the controller until a full reset. 
- **Integral Windup**: When a mechanism physically stalls, `totalError` accumulates infinitely. Upon recovering, the PID controller will aggressively overshoot while waiting for the massive error term to slowly unwind. A maximum windup bound is strictly required.
- **Output Clamping**: Without `minOutput` and `maxOutput` in `PIDController`, anomalous error spikes could command dangerously high voltages to physical motors, risking hardware damage.
- **GC Allocations**: Control loops (like `calculate()`) often run at 100Hz to 1000Hz. The 13+ heap allocations per cycle in `LQRController` will trigger frequent Garbage Collector (GC) pauses on the JVM/Android runtime. GC pauses cause unpredictable latency spikes, which can violently destabilize an LQR controller. Allocations inside the loop must be strictly eliminated using pre-allocated workspaces and mutative mathematical operations.

## Caveats
- Adding strict bounds and zero-allocation logic will inflate the file sizes and alter the API slightly. For example, `LQRController.calculate` may need to accept a pre-allocated output array (e.g. `fun calculate(..., outU: DoubleArray)`) to be truly zero-allocation.
- `PIDController` needs additional properties `maxIntegralSum`, `minOutput`, and `maxOutput`. Backwards compatibility of constructors will need to be managed or broken intentionally.

## Conclusion
**Fix Strategy**:
1. **PIDController**: 
   - Add bounds checks: `require(!measurement.isNaN())`, `require(dtSeconds > 0.0)`.
   - Add `var maxIntegralSum`, and clamp `totalError` to `[-maxIntegralSum, maxIntegralSum]`.
   - Add `var minOutput`, `var maxOutput`, and clamp the final return value using `coerceIn()`.
2. **LQRController**: 
   - **Zero-Allocation**: Add `out` parameter to matrix operations: `fun multiply(other: Matrix, out: Matrix)`.
   - Pre-allocate temporary matrices as instance variables (`stateError`, `prediction`, `correction`, `measuredDiff`, `saturatedU`).
   - Change `calculate(..., outArray: DoubleArray)` to mutate an existing array instead of returning a new one.
   - Refactor `Double?` slew limit to use a default `-1.0` indicator to bypass boxing allocations.
   - Add strict checks: `require(dtSeconds > 0)`.
3. **GravityFeedforward**:
   - Add `require(kG.isFinite())` and `require(angleRadians.isFinite())`.

## Verification Method
- **Static Analysis**: Visually inspect the `calculate` loops for `new` keyword semantics (or implicit constructor calls like `Matrix()`, `DoubleArray()`) to confirm 0 heap allocations per cycle.
- **Unit Testing**: Write unit tests for all controllers using extreme values (`NaN`, negative `dtSeconds`) to confirm `IllegalArgumentException` is thrown reliably instead of producing invalid mathematics.
- **Memory Profiling**: Execute `LQRController.calculate()` in a `10,000` iteration `while` loop within a test method; monitor memory allocations with a profiler to ensure absolute zero growth.
