# Handoff Report: EKF Estimation Hardening

## Observation
- **GC Allocations in Hot-Paths**: `PoseEstimator.kt` methods (`addOdometryObservation` and `addVisionMeasurement`) create new `PoseEstimatorState`, `PoseHistoryEntry`, and collection instances on every call (e.g., `(state.history + newEntry).takeLast(MAX_HISTORY_SIZE)` and `state.history.toMutableList()`).
- **Immutable Math Classes**: `Matrix3x3` and `Vector3` are immutable `data class`es. Operations like `+`, `*`, and `inverse()` allocate new object instances, causing high GC pressure during EKF matrix operations (e.g., `baseEntry.covariance + R`, `S.inverse()`, `K * y`).
- **Matrix Singularity**: In `Matrix3x3.kt:66`, the inversion singularity check uses exact floating-point comparison `det == 0.0` instead of checking against a small epsilon.
- **Division-by-Zero Risk**: In `PoseEstimator.kt:219`, `val multiTagFactor = 1.0 / kotlin.math.sqrt(numTags.toDouble())` can cause division by zero if `numTags` is 0.
- **Missing NaN/Infinity Bounds**: Neither `PoseEstimator.kt` nor `Matrix3x3.kt` validate inputs for `NaN` or `Infinity` (e.g., `deltaTranslation`, `gyroRateRadPerSec`, `visionStdDevs`, `measurement`).

## Logic Chain
1. **Eliminate GC Allocations**: The current immutable architecture creates significant object churn at high frequencies. Replacing the `List<PoseHistoryEntry>` with a pre-allocated circular array buffer, and making math classes mutable (e.g., `fun add(other: Matrix3x3, out: Matrix3x3)`) or using primitive `DoubleArray`s, will eliminate allocations in the `update()` loop.
2. **Prevent Matrix Singularities**: `det == 0.0` is unreliable for floating-point values. Near-zero determinants can lead to massive values (effectively infinity) during inversion. An epsilon check (e.g., `abs(det) < 1e-9`) prevents numeric explosion when computing `S_inv`.
3. **Fix Division-by-Zero**: If a vision frame detects 0 tags but still passes a measurement (or defaults `numTags` to 0), `multiTagFactor` becomes `Infinity`, corrupting the covariance matrix `R`. Guarding `numTags > 0` is required.
4. **NaN/Infinity Checking**: If any sensor returns `NaN` or `Infinity`, it will instantly infect the entire EKF state (pose and covariance matrices) due to the recursive nature of the filter. Strict `isNaN()` and `isInfinite()` bounds checking at the entry points of `addOdometryObservation` and `addVisionMeasurement` will prevent state corruption.

## Caveats
- Changing from immutable `data class`es to mutable pre-allocated buffers will break API compatibility. External systems relying on holding historical `PoseEstimatorState` references will see their data mutated unless deep copies are explicitly made.
- The epsilon value chosen for singularity checks (`1e-9` vs `1e-6`) might need tuning based on the actual variance of the system's sensors.
- Dropping `NaN`/`Infinity` measurements is correct, but we must ensure the estimator gracefully handles skipped updates without diverging.

## Conclusion
To achieve Milestone 1.1, the EKF should be refactored as follows:
1. Validate all inputs in `addOdometryObservation` and `addVisionMeasurement` with `!input.isNaN()` and `!input.isInfinite()`, returning early or discarding if invalid. Ensure `numTags > 0`.
2. Update `Matrix3x3.inverse()` to check `abs(det) < 1e-9`.
3. Refactor `PoseEstimatorState` and `PoseEstimator` to use a pre-allocated array-based circular buffer for history instead of allocating new `List`s.
4. Add mutable in-place operations to `Matrix3x3` and `Vector3` (or back them with reused `DoubleArray` pools) to stop allocating new objects during matrix math.

## Verification Method
- **Static Analysis/Review**: Check that no `new` object allocations (or `copy()`/`+` on lists) occur inside `addOdometryObservation` and `addVisionMeasurement`.
- **Unit Testing**: Inject `NaN` and `Infinity` into sensor inputs and verify the state does not become `NaN`. Pass a singular matrix to `inverse()` and ensure it safely returns a zero matrix. Pass `numTags = 0` and verify it does not throw or corrupt state. Execute the project test command (e.g., `./gradlew test`) to ensure regressions are not introduced.
