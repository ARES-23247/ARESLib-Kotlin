# Phase 46: Pose Disambiguation and Outlier Filtering - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (Autonomous Smart Discuss)

<domain>
## Phase Boundary

Implement an outlier rejection filter for vision measurements to discard noisy, distant, or ambiguous AprilTag poses before they are fused into the Extended Kalman Filter.

</domain>

<decisions>
## Implementation Decisions

### 1. Reject Criteria Configuration
Create a configuration class or container `com.areslib.hardware.vision.VisionFilterConfig`:
- `maxDistanceMeters: Double = 6.0` (AprilTag precision degrades non-linearly with distance).
- `maxAmbiguity: Double = 0.2` (Ambiguity > 0.2 represents high-variance pose estimates).
- `maxRotationDeviationRad: Double = Math.toRadians(15.0)` (Gyro heading cross-reference tolerance).

### 2. Implementation Class
Create `com.areslib.hardware.vision.VisionOutlierFilter`:
- `fun isValid(measurement: VisionMeasurement, robotHeadingRad: Double, robotPose: Pose2d): Boolean`
  - Check distance: `measurement.targetPose.translation.toTranslation2d().distance(robotPose.translation) <= maxDistance`
  - Check ambiguity: `measurement.ambiguity <= maxAmbiguity` (Only applicable if ambiguity >= 0; negative could represent invalid/unused).
  - Check yaw alignment: Verify tag translation/rotation relative to gyro heading. Since gyro is highly reliable, any vision yaw that drifts more than `maxRotationDeviationRad` should be discarded.

</decisions>

<code_context>
## Existing Code Insights
- We have 3D geometry types in `com.areslib.math.Pose3d`, `Translation3d`, `Rotation3d`.
- Robot state has `odometryHeading` and `poseEstimator`.

</code_context>

<specifics>
## Specific Ideas
- Distance check: calculate distance between tag pose and current robot pose.
- Yaw check: `val deviation = abs(normalizeAngle(measurement.targetPose.rotation.z - robotHeadingRad))` must be `< maxRotationDeviationRad`.

</specifics>

<deferred>
## Deferred Ideas
- Multi-tag disambiguation where multiple tags are averaged with varying covariance weights based on distance (deferred to EKF phase).

</deferred>
