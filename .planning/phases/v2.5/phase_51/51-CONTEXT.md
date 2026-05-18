# Phase 51: Dynamic Odometry Covariance Scaling (IMU Tilt & Beaching Safe) - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

The objective of Phase 51 is to make EKF pose estimation robust to wheel slippage and beaching by monitoring chassis pitch and roll:
1. **Dynamic Noise Scaling**: If the robot tilt exceeds 8 degrees, scale up the odometry covariance $Q$ by $100\times$ (modifying EKF trust weight to heavily favor visual measurements).
2. **Beaching Lockout / Pose Freeze**: If the robot tilt exceeds 15 degrees, treat the robot as beached (wheel inputs are spinning in the air or hung up). Fully ignore/freeze the odometry input delta updates to prevent pose estimation runaway.

</domain>

<decisions>
## Implementation Decisions

1. **`addOdometryObservation` Signature**:
   - Add optional arguments `pitchDegrees: Double = 0.0` and `rollDegrees: Double = 0.0` to `addOdometryObservation`.
2. **Tilt Evaluation Formula**:
   - Compute `tiltDegrees = sqrt(pitchDegrees * pitchDegrees + rollDegrees * rollDegrees)`.
3. **Beaching Lockout Flow**:
   - If `tiltDegrees > 15.0`: Do not apply the `deltaTranslation` or `deltaHeading`. Retain the previous pose and covariance, but still append a new history entry with the current timestamp to keep the history trace continuous for retrospective latency corrections.
4. **Covariance Scaling Flow**:
   - If `tiltDegrees > 8.0`: Apply delta updates as normal, but propagate covariance using $P = P + Q \times 100.0$.
   - Otherwise, propagate covariance using $P = P + Q$.

</decisions>

<code_context>
## Existing Code Insights

- `PoseEstimator.kt` defines `addOdometryObservation`.
- `RootReducer.kt` forwards IMU measurements from `DriveHardwareUpdate` to state but not yet to `addOdometryObservation`.

</code_context>

<deferred>
## Deferred Ideas

- None.

</deferred>
