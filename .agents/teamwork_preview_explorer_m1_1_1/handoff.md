# Handoff Report: Milestone 1.1 Estimation Hardening

## Observation
*   **Numerical Instability & Singularities:**
    *   In `Matrix3x3.kt:66`, `inverse()` checks for singularity using an exact zero check (`if (det == 0.0)`). If singular, it returns an all-zero `Matrix3x3()`, which zeros out covariance maliciously if propagated.
    *   In `PoseEstimator.kt:219`, `val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())` does not check if `numTags` is zero, leading to division-by-zero (`Infinity`).
    *   There are no `isNaN()` or `isInfinite()` validations on sensor inputs (`gyroRateRadPerSec`, `deltaTranslation`, etc.) in `addOdometryObservation` and `addVisionMeasurement`.
*   **GC Allocations in Hot Paths:**
    *   `PoseEstimatorState`, `PoseHistoryEntry`, `Matrix3x3`, and `Vector3` are all immutable data classes.
    *   Every math operation in `Matrix3x3.kt` (e.g., `plus`, `times`, `inverse`) creates a new object instance.
    *   In `PoseEstimator.kt:148` (`addOdometryObservation`), `state.history + newEntry` and `takeLast(MAX_HISTORY_SIZE)` allocate multiple new list instances per tick.
    *   In `PoseEstimator.kt` (`addVisionMeasurement`), the history replay loop allocates a new `Matrix3x3` and `PoseHistoryEntry` per iteration (`currentCov = currentCov + Q`; `newHistory[i] = PoseHistoryEntry(...)`), and converts lists dynamically via `toMutableList()` and `toList()`.

## Logic Chain
1.  **Strict Bounds Checking:** Because floating-point inaccuracies can cause near-zero determinants, the `det == 0.0` check is insufficient and will attempt to invert near-singular matrices, resulting in `Infinity` values. Returning an all-zero matrix upon failure silently poisons the EKF pipeline. Division by `numTags=0` will inject `Infinity` directly into the noise model. Unchecked sensor `NaN` values will propagate unconditionally, corrupting the entire state history.
2.  **GC Allocations:** The robot's odometry loop updates at high frequencies (e.g., 50-200Hz). Generating 10-20 short-lived objects (matrices, list copies) per tick forces the JVM/ART garbage collector to run frequently. This triggers GC pauses (stop-the-world), which manifests as severe latency spikes and lost odometry ticks during autonomous routines.
3.  **Required Fixes:** To achieve Milestone 1.1, the immutable designs of `Matrix3x3` and `Vector3` must be refactored to support in-place mutability (e.g., `matrixA.add(matrixB, resultMatrix)`), or be backed by a primitive array pool. `PoseEstimatorState`'s history list must be replaced with a pre-allocated circular ring buffer (primitive array or pre-instantiated object array) to permanently eliminate dynamic list allocations. Epsilon thresholding (`abs(det) < 1e-9`) and explicit `isNaN`/`isInfinite`/`numTags == 0` early-returns are required to secure the numerical pipeline.

## Caveats
*   Refactoring `PoseEstimatorState` and math classes from immutable to mutable changes their API semantics. Downstream consumers referencing these objects might accidentally read a mutated state if they expect a frozen snapshot. Deep-copy snapshot methods will be required for external reads.
*   Pre-allocating a ring buffer of objects consumes slightly more baseline heap memory, though this is negligible compared to the latency savings.

## Conclusion
The current implementation of the EKF estimator fails both the numerical stability and memory allocation requirements of Milestone 1.1. 
The recommended fix strategy is:
1.  Add `isNaN` / `isInfinite` guards at the start of `addOdometryObservation` and `addVisionMeasurement`.
2.  Add a `numTags > 0` check and use `abs(det) < 1e-9` for matrix singularity, failing gracefully (e.g., aborting the vision update) rather than returning an all-zero matrix.
3.  Redesign `Matrix3x3` and `Vector3` to allow in-place mutations without instantiating new objects.
4.  Replace the `List<PoseHistoryEntry>` in `PoseEstimator` with a statically-sized, pre-allocated Circular Ring Buffer that overwrites older entries in place instead of creating new ones.

## Verification Method
*   **Fuzz Testing:** Write a unit test that injects `NaN`, `Infinity`, and `numTags=0` into both `addOdometryObservation` and `addVisionMeasurement`. Verify the state rejects these inputs without crashing or corrupting existing values.
*   **Matrix Singularity Test:** Pass a singular covariance matrix (`Matrix3x3()` of zeroes) into the update step and ensure the fallback mechanism correctly ignores the update.
*   **Allocation Profiling:** Write a JMH benchmark or simulate a high-frequency (1000 Hz) run of the estimator in a test. Run with Android Studio Profiler or VisualVM to verify that object allocation rate drops to 0 bytes/sec during steady-state `update()` loops.
*   **Regression Validation:** Run the standard project tests (e.g., `./gradlew test`) to verify the EKF math outputs haven't structurally changed following the mutability refactor.
