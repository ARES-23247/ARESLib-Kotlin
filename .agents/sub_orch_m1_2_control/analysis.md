# M1.2 Control Hardening Synthesis

## Consensus
- **PIDController**:
  - `calculate()` uses primitive values (no GC allocations) but lacks numerical bounds and integral windup protections.
  - Requires `Double.isFinite()` checks on all inputs (`measurement`, `setpoint`, `dtSeconds`), plus `dtSeconds > 0`.
  - Integral windup needs capped bounds (e.g. `maxIntegralSum`).
  - Output requires a final `coerceIn(minOutput, maxOutput)` clamp before returning.
- **LQRController**:
  - `calculate()` has severe GC allocation issues. Every `Matrix` operation creates new objects and arrays.
  - Fix Strategy: Add zero-allocation, mutating methods to `Matrix` (e.g. `addInto`, `subtractInto`, `multiplyInto`, `getColumnInto`). Pre-allocate all temporary `Matrix` objects (`stateError`, `rawU`, `saturatedU`, etc.) in `LQRController` during initialization. Return a pre-allocated `DoubleArray` from `calculate()`.
  - Boxed primitive `Double?` on slew limits causes GC allocation; switch to a negative default flag.
  - Requires numerical checks on inputs and `dtSeconds`.
- **GravityFeedforward**:
  - Needs `isFinite()` checks on `kG` and `angleRadians`.

## Resolved Conflicts / Scope Clarifications
- Matrix Singularity: The scope requires singularity checks, which already exist in `Matrix.inverse()`. These will correctly crash the loop fast.
- Thread safety in LQR: Eliminating GC allocations by reusing internal arrays means `LQRController` is no longer thread-safe across concurrent updates. This is expected in single-threaded control loops, but should be documented.

## Output Required
- Implement changes in `PIDController.kt`, `LQRController.kt`, `GravityFeedforward.kt`, and `Matrix.kt`.
- Write unit tests verifying edge cases (`NaN`, negative `dtSeconds`).
- Run `./gradlew build` and `./gradlew test` (or Windows equivalent) to verify everything passes.
