# Handoff Report: Milestone 1.1 Estimation Hardening

## Observation
- `PoseEstimator.kt:272`: `val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())` divides by `numTags`. There is no check to ensure `numTags > 0`. 
- `Matrix3x3.kt:66`: `if (kotlin.math.abs(det) < 1e-9) return Matrix3x3()` returns a matrix entirely filled with zeroes when the matrix is singular.
- `PoseEstimator.kt:304`: During `addVisionMeasurement`, if `S` is singular, `S.inverse()` yields a zero matrix. This propagates to `val S_inv_y = S_inv * y`, yielding `Vector3(0.0, 0.0, 0.0)`. Consequently, the Mahalanobis distance `dMSquared` computes to `0.0`. 
- `PoseEstimator.kt:150` & `196`: `val newHistory = (state.history + newEntry).takeLast(MAX_HISTORY_SIZE)` allocates a new `PoseHistoryEntry` object and two `ArrayList` instances (via `+` and `takeLast`) on every odometry update.
- `PoseEstimator.kt` and `Matrix3x3.kt`: The Kalman filter equations (`+`, `*`, `inverse()`) rely on the immutable properties of `Matrix3x3` and `PoseEstimatorState`, requiring `.copy()` and new object allocations on every hot-path mathematical operation.
- `PoseEstimator.kt:36`: `HistoryBuffer.deepCopy()` uses the data class `.copy()` function, allocating 50 new `PoseHistoryEntry` objects per copy.

## Logic Chain
1. **Division by Zero (`numTags`)**: If the vision system yields a measurement with `numTags = 0` (e.g., target lost right as pipeline dispatched), `multiTagFactor` becomes `Infinity`. This causes `scaledStdDevs`, `R`, and `S` to inherit `Infinity`/`NaN`.
2. **Singular Matrix Outlier Bypass**: If `S` becomes singular (due to zero covariance, NaN propagation, or extreme scaling), `Matrix3x3.inverse()` quietly returns a zero matrix.
3. **Flawed Rejection**: With `S_inv` as a zero matrix, `dMSquared` becomes 0. A Mahalanobis distance of 0 implies a perfect statistical match. The outlier filter (`dMSquared > mahalanobisThreshold`) evaluates to false, failing to reject the bad measurement. The Kalman gain `K` becomes zero, resulting in a no-op state update that needlessly wastes CPU cycles replaying the odometry history.
4. **GC Allocations in List Processing**: `(state.history + newEntry).takeLast(MAX_HISTORY_SIZE)` executes at odometry frequency (often >100Hz). Iterating and copying into standard Kotlin `List`s completely bypasses the benefits of the pre-allocated `HistoryBuffer` array.
5. **GC Allocations in Matrix Math**: The Extended Kalman Filter covariance equations instantiate ~10 temporary `Matrix3x3` and `Vector3` objects per frame. Over continuous execution, this creates severe garbage collection pressure on ART/RoboRIO platforms.
6. **GC Allocations in Deep Copy**: Even if `List` creation is mitigated, relying on `HistoryBuffer.deepCopy()` or `.copy()` generates garbage.

## Caveats
- Transitioning `PoseEstimatorState`, `Matrix3x3`, and `Pose2d` to a mutable pattern represents a shift from functional immutability to an imperative, side-effecting approach. The upstream systems calling the estimator must be aware that the state object they read might be modified in-place on the next tick.
- Changing `Matrix3x3.inverse()` to return a nullable type (`Matrix3x3?`) or explicitly throw an error will require refactoring all callers.
- We assume that `numTags` can realistically be 0. It is best practice to bound-check it regardless.

## Conclusion
**Recommended Fix Strategy:**
1. **Numerical Bounds Guard**: Add a direct `if (numTags <= 0) return state` guard in `addVisionMeasurement`.
2. **Singularity Guard**: Modify `Matrix3x3.inverse()` to return `null` instead of a zero matrix when `det < 1e-9`. In `PoseEstimator.kt`, safely abort the vision update (`if (S_inv == null) return state`) to prevent mathematically invalid matrix math and bypassing the outlier rejection.
3. **Eliminate List Allocations**: Remove the `.takeLast(MAX_HISTORY_SIZE)` usage. Instead, make `PoseEstimatorState` hold a single mutable `HistoryBuffer` and use `history.addEntry(...)` to update the ring-buffer in-place.
4. **Eliminate Math & State Allocations**: 
   - Refactor `Matrix3x3` to use mutable primitive properties (`var m00`, etc.) or a primitive `DoubleArray` buffer.
   - Introduce in-place operational methods on `Matrix3x3` (e.g., `addInPlace(other)`, `multiplyInPlace(other)`) so the EKF covariance equations modify an existing matrix buffer instead of returning new instances.
   - Refactor `HistoryBuffer.deepCopy()` to copy primitive properties between entries (`dst.timestampMs = src.timestampMs`) instead of invoking `.copy()`.
   - Update `PoseEstimatorState` to be mutable, avoiding `state.copy()` entirely by applying updates directly.

## Verification Method
1. **Tests**: Run `./gradlew test` (or `./gradlew assembleDebug` for FTC projects) to ensure compilation passes without errors.
2. **Bounds Test**: Write a unit test providing a vision measurement with `numTags = 0` and extreme/zero standard deviations. Verify that `PoseEstimator` safely discards the update without crashing, emitting `NaN` values, or bypassing the Mahalanobis filter.
3. **Allocation Profiling**: Write a high-frequency benchmark loop invoking `addOdometryObservation` and `addVisionMeasurement` 10,000 times. Profile with JVM heap analysis or Android Studio Memory Profiler to assert zero object allocations (`ArrayList`, `PoseHistoryEntry`, `Matrix3x3`) occur inside the hot loop.
