# Phase 48: High-Fidelity Vision Simulation & Noise Rejection - Context

**Gathered:** 2026-05-18 (assumptions mode)
**Status:** Ready for planning

<domain>
## Phase Boundary

Simulate and validate AprilTag visual localization inside the simulator under noise, latency, and outlier scenarios to guarantee the EKF's resilience and mathematical correctness.
</domain>

<decisions>
## Implementation Decisions

### Vision Noise & Latency Generation
- **D-01:** Implement a realistic Gaussian random noise model for translation ($x, y$) and heading.
- **D-02:** Maintain a sliding history of observations to simulate camera latency (e.g., 80ms delay) and deliver out-of-order measurements to the buffer.
- **D-03:** Emulate random outliers with high ambiguity ($>0.3$) or extreme coordinate values to verify rejection behavior.

### Simulation Feedback
- **D-04:** Stream EKF-estimated poses alongside simulated ground-truth poses to AdvantageScope under designated NetworkTable fields (`EstimatedPose` vs `TruePose`) for real-time visualization.
</decisions>

<canonical_refs>
## Canonical References

- [VisionMeasurementBuffer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/hardware/vision/VisionMeasurementBuffer.kt)
- [VisionOutlierFilter.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/hardware/vision/VisionOutlierFilter.kt)
- [PoseEstimator.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/math/PoseEstimator.kt)
- [DesktopSimLauncher.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/simulator/src/main/kotlin/com/areslib/sim/DesktopSimLauncher.kt)
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PoseEstimator.kt` holds state history and performs the retroactive EKF fusion updates.
- `SwerveRobotDouble.kt` models the digital twin chassis movements.
</code_context>

<specifics>
## Specific Ideas
- Set up a few standard FRC AprilTag target coordinates in the virtual field (e.g., origin center and corners).
</specifics>

<deferred>
## Deferred Ideas
None.
</deferred>
