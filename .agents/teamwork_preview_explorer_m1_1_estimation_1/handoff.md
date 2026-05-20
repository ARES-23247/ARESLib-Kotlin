# Handoff Report: PoseEstimator & Matrix3x3 Analysis

## Observation

1. **Numerical Bounds Issues:**
   - `Matrix3x3.kt:66`: The singularity check `if (det == 0.0)` uses a strict equality check for floating-point zero.
   - `Matrix3x3.kt:62`: The calculation of `det` can produce `NaN` or `Infinity` if the matrix already contains `NaN` or large values. This is not checked before performing `1.0 / det`.
   - `PoseEstimator.kt:219`: `val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())` divides by zero if `numTags` is 0, resulting in `Infinity`.
   - `PoseEstimator.kt:256-258`: The Mahalanobis outlier rejection check `if (dMSquared > mahalanobisThreshold)` evaluates to `false` if `dMSquared` is `NaN`.

2. **GC Allocations in Hot Paths:**
   - `PoseEstimator.kt:148`: `val newHistory = (state.history + newEntry).takeLast(MAX_HISTORY_SIZE)` allocates two new Lists and copies up to 50 elements every single odometry tick.
   - `PoseEstimator.kt:135, 145`: `Q * tiltScale * slipScale` and `state.covariance + currentQ` allocate three separate `Matrix3x3` instances per tick.
   - `PoseEstimator.kt:281`: `state.history.toMutableList()` allocates a new list, followed by a `for` loop (lines 285-303) that allocates new `Pose2d`, `Matrix3x3`, and `PoseHistoryEntry` objects on each historical replay step.
   - `Matrix3x3.kt` and `Vector3.kt`: All arithmetic operators (`+`, `-`, `*`) and `.inverse()` are implemented immutably, returning brand new object instances for every operation.

## Logic Chain

- **Strict floating-point equality (`det == 0.0`)** is unreliable due to IEEE-754 precision limitations. A near-singular matrix could yield a determinant like `1e-15`, which bypasses the zero check but causes `invDet` to explode to infinity, flooding the covariance matrix with `Infinity` or `NaN`.
- **Unhandled NaN conditions**: In Kotlin, comparisons like `NaN > 12.0` evaluate to `false`. Therefore, if a matrix singularity or a division-by-zero introduces `NaN` into `dMSquared`, the Mahalanobis outlier rejection is bypassed entirely, permanently corrupting the estimator state with `NaN`.
- **Division by zero**: `numTags` can be supplied as 0. `1.0 / sqrt(0.0)` evaluates to `Infinity`. This propagates through `scaledStdDevs`, `R`, `S`, `S_inv`, and finally infects the core state covariance.
- **Immutability causing GC pressure**: `PoseEstimatorState`, `Matrix3x3`, and `Vector3` are implemented as deeply immutable structures. The high-frequency `update` loops (~100Hz) recreate the entire `history` list and dozens of intermediate matrix objects per frame. In resource-constrained environments like Android ART (FTC) or RoboRIO (FRC), this continuous allocation triggers frequent Garbage Collection pauses (stop-the-world), leading to loop jitter, missed control cycles, and degraded odometry accuracy.

## Caveats

- Refactoring `PoseEstimatorState`, `Matrix3x3`, and `PoseHistoryEntry` to be mutable, or introducing pre-allocated object pools, breaks the current pure functional programming paradigm used in the estimator. Callers relying on state immutability will need to adapt.
- `Pose2d` and `Rotation2d` are referenced in the hot paths but are not included in the target files of this investigation. They will likely also need mutable counterparts or primitive backing to fully eliminate all object allocations in the scope.

## Conclusion

The estimator is currently vulnerable to state-corrupting `NaN`/`Infinity` propagation and matrix singularity explosions. Furthermore, it suffers from severe GC allocation overhead due to List copies and Matrix instantiations in the high-frequency update loops.

**Fix Strategy**:
1. **Numerical Hardening**: Replace `det == 0.0` with an epsilon threshold check (e.g., `kotlin.math.abs(det) < 1e-9`) and explicit `isNaN()`/`isInfinite()` guards in `Matrix3x3.inverse()`. Add `isNaN()` guards to the Mahalanobis rejection block and ensure `numTags = max(1, numTags)`.
2. **Eliminate GC Allocations**: 
   - Refactor `PoseEstimatorState.history` from an immutable `List` to a fixed-size, primitive-backed circular buffer array (ring buffer) with a mutable head index.
   - Transition `Matrix3x3` and `Vector3` to use mutating functions (e.g., `addInPlace(other)`, `multiplyInto(dest)`) instead of operator overloads that return new instances.
   - Instantiate object-level scratchpad buffers (e.g., `private val tempS = Matrix3x3()`) inside `PoseEstimator` for use during intermediate computations to avoid per-frame allocations.

## Verification Method

1. **Compilation**: Run the project build command (e.g., `./gradlew build` or `./gradlew test`) to ensure the changes compile.
2. **Bounds Testing**: Write a unit test passing `numTags = 0` and injecting a singular/corrupted covariance matrix to verify the system rejects the update gracefully instead of propagating `NaN` to the estimated pose.
3. **Allocation Profiling**: Use a memory profiler (e.g., Android Studio Profiler or VisualVM) on a loop executing `addOdometryObservation` and `addVisionMeasurement` 1000 times. Verify that the memory allocation delta is zero (or strictly negligible) compared to baseline after initialization.
