# Phase 50: Advanced Outlier Filter (3D Boundaries, Angular Speed, Acceleration/Shock Lockouts) - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

The objective of Phase 50 is to implement advanced outlier rejection filters in `VisionOutlierFilter` to reject visually simulated/detected tags that:
1. Violate 3D spatial boundaries (out-of-bounds field limits, floating or underground Z limits).
2. Are captured during high rotational speeds ($|\omega| > 2.0\text{ rad/s}$), preventing motion-blurred tag updates.
3. Are captured during high-G collisions or chassis impacts ($> 2.5\text{ G}$ shock), protecting the EKF against camera shake.

</domain>

<decisions>
## Implementation Decisions

1. **Config Expansion in `VisionFilterConfig`**:
   - `minFieldX: Double = -1.825`
   - `maxFieldX: Double = 1.825`
   - `minFieldY: Double = -1.825`
   - `maxFieldY: Double = 1.825`
   - `minFieldZ: Double = -0.1`
   - `maxFieldZ: Double = 0.5`
   - `maxAngularVelocityRadPerSec: Double = 2.0`
   - `maxAccelerationG: Double = 2.5`
2. **`isValid` Method Signature Expansion**:
   - Expand `isValid` with default parameters for `angularVelocityRadPerSec: Double = 0.0`, `linearAccelXG: Double = 0.0`, `linearAccelYG: Double = 0.0`, and `linearAccelZG: Double = 1.0`.
   - By leveraging default parameters, we guarantee existing tests and code paths continue to compile without immediate changes.
3. **Logic Flow in `isValid`**:
   - Validate 3D coordinates of `measurement.targetPose`: Reject if `targetPose.x` or `targetPose.y` is outside `[minFieldX, maxFieldX]` / `[minFieldY, maxFieldY]`, or `targetPose.z` is outside `[minFieldZ, maxFieldZ]`.
   - Validate angular velocity: Reject if `abs(angularVelocityRadPerSec) > maxAngularVelocityRadPerSec`.
   - Validate collision shock: Calculate magnitude of dynamic acceleration `sqrt(linearAccelXG * linearAccelXG + linearAccelYG * linearAccelYG + dynamicZ * dynamicZ)` (where `dynamicZ` is `linearAccelZG - 1.0` if `linearAccelZG != 0.0` to handle gravity-compensated vs raw IMU setups). Reject if the magnitude exceeds `maxAccelerationG`.
4. **RootReducer Integration**:
   - Pass state variables `angularVelocityRadiansPerSecond`, `xAccelerationG`, `yAccelerationG`, and `zAccelerationG` to `isValid` inside `RootReducer.kt`.

</decisions>

<code_context>
## Existing Code Insights

- `VisionOutlierFilter.kt` implements the basic outlier rejection.
- `RootReducer.kt` instantiates and invokes `VisionOutlierFilter.isValid(...)`.

</code_context>

<specifics>
## Specific Ideas

- Ensure absolute field limits are default configured to standard FTC size ($3.65\text{m} \times 3.65\text{m}$ centered at 0), meaning bounds of $[-1.825, 1.825]$.

</specifics>

<deferred>
## Deferred Ideas

- None.

</deferred>
