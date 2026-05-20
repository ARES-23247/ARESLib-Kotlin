# Control Hardening Explorer Handoff

## Observation
### `PIDController.kt`
- **Line 63-65**: The condition `if (dtSeconds > 0)` protects `totalError` but does not prevent `dtSeconds` from being an arbitrarily small positive value.
- **Line 67**: `(error - prevError) / dtSeconds` can cause floating-point explosion (Infinity or extremely high values) if `dtSeconds` is microscopically small.
- **Line 64**: `totalError += error * dtSeconds` grows unboundedly. There is no integral windup limit.
- **Line 70**: `p * error + i * totalError + d * velocityError` is returned directly without any output clamping.
- **GC Allocations**: None. `calculate` uses pure `Double` primitives.

### `LQRController.kt`
- **Line 123-156**: `calculate()` allocates multiple objects:
  - `yMat`, `xRefMat` (Line 123-124)
  - `saturatedU` (Line 131)
  - `.subtract()`, `.multiply()`, `.multiplyScalar()`, `.add()` each allocate a new `Matrix` instance (Lines 179, 188, 196, 205).
  - `return saturatedU.getColumn(0)` allocates a new `DoubleArray` (Line 170).
- **Line 115**: `dtSeconds` is not validated for a minimum positive threshold, which can cause slew rate limits to break if `dtSeconds <= 0`.
- **Line 237, 246, 261, 298**: Singularity checks exist in `Matrix.inverse()` as `require(abs(det) > 1e-12)`.
- **Line 144**: Output clamping is already present (`coerceIn(minU, maxU)`).

### `GravityFeedforward.kt`
- **GC Allocations**: None.
- Output is calculated via primitive operations but does not enforce numerical bounds or clamping explicitly.

## Logic Chain
1. **PIDController**: Needs explicit limits to meet the scope. Since `dtSeconds` can be effectively zero due to loop jitter, a minimum clamp (e.g., `dtSeconds.coerceAtLeast(1e-6)`) is required. Integral windup limits can be injected by storing `maxIntegral` and clamping `totalError`. Output clamping should be added by introducing `minOutput` and `maxOutput` parameters and using `.coerceIn(minOutput, maxOutput)`.
2. **LQRController**: To eliminate GC allocations in `calculate()`:
   - The class must instantiate intermediate vectors (`_yMat`, `_xRefMat`, `_stateError`, `_prediction`, etc.) as fields during `init` or first pass.
   - The `Matrix` class needs mutating, zero-allocation operations: `addInto`, `subtractInto`, `multiplyInto`.
   - The method should accept an optional `outArray: DoubleArray` to place the results instead of allocating a new array on `getColumn(0)`.
   - The `dtSeconds` should be bounded >= `1e-6` to prevent slew rate bugs.
3. **GravityFeedforward**: Can be enhanced by offering an optional `minOutput` / `maxOutput` bounds clamp for safety, or we assume it relies on the controller combination phase to clamp. The scope implies strict numerical bounds checking, so adding output clamping to Feedforward functions completes the hardening.

## Caveats
- Changing `Matrix` methods from allocating to mutating (or adding `*Into` methods) will make `LQRController` non-thread-safe if multiple threads call `calculate()`. Given it's an FTC/embedded controller, single-threaded loop execution is the standard, so this is an acceptable tradeoff for zero-GC loops.
- Matrix singularity checks already throw exceptions (`IllegalArgumentException`), which will crash the loop. The scope asks for singularity checks; we might want to return a fallback or gracefully bypass `inverse()` errors during `computeFeedbackGains()` rather than hard crashing, or leave it as a fail-fast.

## Conclusion
- **PIDController**: Add `minOutput`, `maxOutput`, and `maxIntegralWindup`. Clamp `dtSeconds` to `1e-6`. Clamp `totalError`. Clamp the return value.
- **LQRController**: Pre-allocate all matrices used in `calculate()`. Add `addInto()`, `subtractInto()`, `multiplyInto()` to `Matrix`. Modify `calculate()` to only use these non-allocating methods. Add `require(dtSeconds >= 1e-6)`.
- **GravityFeedforward**: Add optional clamping extensions or simple `coerceIn` bounds if output limits are required at the unit level.

## Verification Method
- **GC Allocation**: Inspect the compiled byte code or run an allocation profiler / memory dump during a 1000-iteration loop of `LQRController.calculate()` and verify zero allocations.
- **Unit Tests**: Add tests verifying `dtSeconds = 0.0` does not crash PID or LQR. Assert that `totalError` does not exceed `maxIntegralWindup`. Assert the return value never exceeds `maxOutput`.
