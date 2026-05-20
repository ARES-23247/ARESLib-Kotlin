# Handoff Report: Milestone 1.1 Estimation Hardening

## Observation
1. **GC Allocations in Math:** `Matrix3x3.kt` and `PoseEstimator.kt` rely heavily on operator overloading (`+`, `-`, `*`) which create new object instances (`Matrix3x3`, `Vector3`, `Pose2d`) for every mathematical operation and state update.
2. **GC Allocations in History:** `PoseEstimator.kt` at line 102 creates a new list via `(state.history + newEntry).takeLast(MAX_HISTORY_SIZE)`. In `addVisionMeasurement`, it creates a `MutableList` via `.toMutableList()`, replays odometry by creating many `PoseHistoryEntry` and `Pose2d` instances, and returns a new list via `.toList()`.
3. **Weak Matrix Singularity Check:** `Matrix3x3.inverse()` at line 66 only checks for exact zero determinant (`if (det == 0.0)`) which can cause division by extremely small numbers, producing `Infinity` or `NaN` covariance values.
4. **Missing Bounds Checks:** No `NaN` or `Infinity` checks exist for incoming sensor data (e.g., `gyroRateRadPerSec`, `dtSeconds`, `visionStdDevs`, `measurement.targetPose`). There's also no explicit guard against `dtSeconds <= 0` beyond a default replacement.

## Logic Chain
1. **Addressing GC Allocations:** The frequent creation of small objects (`Matrix3x3`, `Vector3`, `Pose2d`) and collections (`List`) in the `update()` equivalent methods generates high garbage volume, triggering GC pauses in hot paths. We must replace the immutable `List` with a pre-allocated fixed-size ring buffer array. We also need to add mutable mathematical operations (e.g., `Matrix3x3.addInto(other, outResult)`) and use pre-allocated scratchpad objects to perform intermediate matrix math without allocating new instances.
2. **Addressing Numerical Bounds:** Floating-point inaccuracies and sensor faults can yield `NaN`, `Infinity`, or near-zero determinants. Checking strictly for these bounds prevents the EKF from collapsing into an unrecoverable state where all subsequent math results in `NaN`. We must introduce `isNaN()` and `isInfinite()` checks at the entry points of `addOdometryObservation` and `addVisionMeasurement`.
3. **Addressing Singularity:** We must update `Matrix3x3.inverse()` to check against a strict epsilon (`abs(det) < 1e-9`) and handle `isNaN` rather than strict equality to `0.0`.

## Caveats
1. Making `PoseEstimatorState` or `Matrix3x3` mutable changes their public API. The docs explicitly state "Immutable chronological state representation". We need to balance this by perhaps hiding the mutability internally or explicitly documenting the change to a RingBuffer-based mutable state object.
2. If `PoseEstimator` methods are called from multiple threads, using single pre-allocated scratchpads for matrix operations could cause race conditions. Assuming single-threaded FTC control loops, this is fine, but `ThreadLocal` could be used if thread safety is required.

## Conclusion
To fulfill Milestone 1.1, the fix strategy requires:
1. **Bounds Checking:** Add strict checks (`isNaN()`, `isInfinite()`) for all incoming sensor parameters in `addOdometryObservation` and `addVisionMeasurement` (early return if invalid). Check `dtSeconds <= 0`.
2. **Matrix Singularity Safe-guards:** Modify `Matrix3x3.inverse()` to reject near-zero and `NaN` determinants (`abs(det) < 1e-9`).
3. **Buffer Pools for Collections:** Replace the immutable `List<PoseHistoryEntry>` in `PoseEstimatorState` with a pre-allocated `Array`-backed ring buffer that overwrites old entries in-place.
4. **Primitive/Mutable Math Objects:** Add mutation methods to `Matrix3x3` and `Vector3` (e.g., `inverseInto(out: Matrix3x3)`) and pre-allocate scratchpad objects in `PoseEstimator` to reuse during intermediate calculations instead of operator overloads that allocate new instances.

## Verification Method
1. **Bounds Testing:** Modify the test suite to pass `NaN` and `Infinity` into `addOdometryObservation` and verify it ignores them without corrupting the state.
2. **Singularity Testing:** Pass a perfectly singular and a near-singular matrix into `Matrix3x3.inverse()` and assert it returns a safe fallback.
3. **GC Testing:** Run a profiler or use an allocation tracker (or `System.identityHashCode()` logging in tests) during a looped `addOdometryObservation` call to assert that zero new object allocations occur per loop tick.
