# Handoff Report: Control Hardening (Milestone 1.2)

## 1. Observation
- **`PIDController.kt`**:
  - The main update loop is `calculate(measurement: Double, dtSeconds: Double)`.
  - There are NO object allocations in this loop (it uses primitives).
  - There is no bounds checking for `NaN` or `Infinity` on inputs (`measurement`, `setpoint`, `dtSeconds`).
  - `totalError` (integral term) is unbounded, which will cause integral windup.
  - There is no final output clamping.
- **`LQRController.kt`**:
  - The main update loop is `calculate(y: DoubleArray, xRef: DoubleArray, dtSeconds: Double)`.
  - There are massive GC allocations per call. Every matrix operation (`subtract`, `multiply`, `multiplyScalar`, `add`) instantiates a new `Matrix` and a new `DoubleArray` under the hood. For example: `xHat.subtract(xRefMat)` creates a new `Matrix`.
  - Matrix slicing via `getColumn(0)` instantiates and returns a new `DoubleArray`.
  - Matrix instantiation via `Matrix(numOutputs, 1, y)` does not copy the array but takes a reference, but it instantiates a new `Matrix` object.
  - In `Matrix.inverse()`, singularity checks already exist (`require(abs(det) > 1e-12) { "Matrix is singular" }`).
  - No `NaN` or `Infinity` validation on `dtSeconds` or input arrays (`y`, `xRef`).
- **`GravityFeedforward.kt`**:
  - `calculateElevator` and `calculateArm` contain no object allocations.
  - Lacks `NaN`/`Infinity` bounds checking on inputs (`angleRadians`, `kG`).

## 2. Logic Chain
1. **Numerical Bounds**: The lack of finite checks means invalid sensor data could infect the control math, producing `NaN` control outputs that silently fail or send arbitrary signals to motors. Adding `Double.isFinite()` requirements to all loop inputs will prevent this.
2. **Integral Windup**: `PIDController` needs properties like `minIntegral` and `maxIntegral` to cap `totalError` accumulation, and `minOutput`/`maxOutput` to cap the return value. 
3. **GC Allocations in LQR**: The creation of temporary objects for intermediate math operations (`stateError`, `rawU`, `saturatedU`, `prediction`, `measuredDiff`, `correction`) heavily degrades performance. To eliminate GC allocations, we must pre-allocate these objects as properties in `LQRController` during initialization, and rewrite the operations in `Matrix` to be in-place (e.g., `fun multiply(other: Matrix, out: Matrix)`).
4. **Pre-allocated Outputs**: Returning new arrays from `calculate()` in `LQRController` violates zero-allocation constraints. We should return a pre-allocated `DoubleArray` member variable that is modified in-place via a new `Matrix.getColumn(col: Int, out: DoubleArray)` method.

## 3. Caveats
- Since the pre-allocated output array in `LQRController.calculate` will be shared across calls, it is NOT thread-safe. A caller storing the returned array reference directly will see its contents change on the next cycle. This is standard for GC-free robotic control loops, but should be documented.
- We assume `Double.isFinite()` is sufficient for numerical bounds, and any `NaN` exception should intentionally crash the control thread or be caught by a higher-level safety system.

## 4. Conclusion
We must implement a set of robust control loop checks and zero-allocation pathways. 
- **`PIDController.kt`**: Add finite checks, integral caps, and output clamping properties.
- **`LQRController.kt`**: Add finite checks for arrays and `dtSeconds`. Pre-allocate all temporary `Matrix` state objects in the class. Add mutating `Matrix` operations (`addInto`, `subtractInto`, `multiplyInto`, `setData`, `getColumnInto`). Refactor `calculate` to use these pre-allocated buffers exclusively.
- **`GravityFeedforward.kt`**: Add finite checks.

## 5. Verification Method
- Execute the build command: `./gradlew build` or `gradlew.bat build` to ensure the project still compiles.
- Run tests: `./gradlew test` or `gradlew.bat test`.
- Inspect `LQRController.kt` line-by-line to verify the word `Matrix(` or `DoubleArray(` never appears within the body of `calculate()`.
- Validate that feeding `Double.NaN` into `PIDController.calculate` throws an `IllegalArgumentException`.
