# Phase 52: Elite Multi-Tag Variance Scaling & Distance Penalization (Kalman Noise Scaling) - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

The objective of Phase 52 is to implement dynamic EKF vision noise scaling ($R$) in `PoseEstimator` based on physical detection parameters:
1. **Quadratic Distance Scaling**: Vision measurement trust decays as a function of distance. Standard deviation is scaled by $\sqrt{1.0 + d^2}$ (so covariance $R$ scales quadratically as $R_{dynamic} = R_{base} \cdot (1.0 + d^2)$).
2. **Multi-Tag Scaling**: When multiple tags are visible in the same frame, standard deviations are scaled down by $\frac{1}{\sqrt{\text{numTags}}}$ (improving overall trust and stability by reducing joint covariance).

</domain>

<decisions>
## Implementation Decisions

1. **`addVisionMeasurement` Signature Update**:
   - Add optional parameter `numTags: Int = 1` to `addVisionMeasurement`.
2. **Tag Database inside `PoseEstimator`**:
   - Define a global Map `TAGS` containing the coordinates of AprilTags:
     - Tag 1: `(1.8, 1.8, 0.5)`
     - Tag 2: `(1.8, -1.8, 0.5)`
     - Tag 3: `(-1.8, 1.8, 0.5)`
     - Tag 4: `(-1.8, -1.8, 0.5)`
3. **Distance Calculation**:
   - Compute distance $d$ between the base estimated robot pose (`baseEntry.pose`) and the target AprilTag coordinate from `TAGS`. Fallback to distance to the measurement's target pose if tagId is not found.
4. **Noise Scaling Formula**:
   - Multi-tag scale factor: `val tagFactor = 1.0 / sqrt(numTags.toDouble())`
   - Distance scale factor: `val distFactor = sqrt(1.0 + distance * distance)`
   - Scaled standard deviation vector: `val finalStdDevs = visionStdDevs * (tagFactor * distFactor)`
5. **RootReducer Integration**:
   - In `RootReducer.kt`, pass `numTags = validMeasurements.size` when invoking `addVisionMeasurement`.

</decisions>

<code_context>
## Existing Code Insights

- `PoseEstimator.kt` implements EKF vision fusion.
- `RootReducer.kt` processes multiple visual frames sequentially.

</code_context>

<deferred>
## Deferred Ideas

- None.

</deferred>
