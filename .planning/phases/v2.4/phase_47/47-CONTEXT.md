# Phase 47: Extended Kalman Filter Integration - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (Autonomous Smart Discuss)

<domain>
## Phase Boundary

Integrate the `VisionOutlierFilter` into the active `rootReducer` and `VisionReducer` pipeline to filter out-of-order, noisy, or ambiguous AprilTag measurements before they are fused into the retroactive Extended Kalman Filter state.

</domain>

<decisions>
## Implementation Decisions

### 1. Root Reducer Action Handling
Update the `RobotAction.VisionMeasurementsReceived` block in `RootReducer.kt`:
- Instantiate `VisionOutlierFilter`.
- Extract the current `robotPose` and `robotHeading` from `state.drive.poseEstimator.estimatedPose`.
- Filter `action.measurements` using `filter.isValid(measurement, robotHeading, robotPose)`.
- Use the filtered list to perform EKF fusion using `PoseEstimator.addVisionMeasurement()`.
- Pass a filtered copy of the action to `VisionReducer.reduce` to update the global vision state.

### 2. Action Interface Copy
Add a `.copy()` or equivalent helper/data class structure to `RobotAction.VisionMeasurementsReceived` if it is not already a data class to allow easy filtering.

</decisions>

<code_context>
## Existing Code Insights
- `RobotAction.VisionMeasurementsReceived` is an action in `com.areslib.action.RobotAction`. Let's verify its definition.

</code_context>
