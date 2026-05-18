# Phase 73: AdvantageScope 3D Visualizer & Stream - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (Smart Autonomous Discuss)

<domain>
## Phase Boundary

This phase delivers high-fidelity 3D visualization of the Marvin 19 robot within AdvantageScope. It bridges the pure functional simulated state and live telemetry outputs to standard AdvantageScope formats, ensuring smooth rendering of:
1. The robot chassis pose on the 3D field.
2. The 3D relative mechanism poses of the intake pivot, cowl hood angle, and flywheel.
3. Individual swerve module actual and target states (translating degrees to radians).
4. Dyn4j physical active game pieces (balls) spawned on the field.
5. EKF covariance bounds for real-time localization confidence monitoring.

</domain>

<decisions>
## Implementation Decisions

### 3D Component Config
- **Swerve Module State Angles:** Convert swerve module logging in `ARESRobot.kt` from degrees to radians, as AdvantageScope Swerve Module visualizer strictly expects radians (`[angle_rad, speed_m_s]`).
- **3D Pose Formats:** Maintain standard WPILib `Pose3d` structure `[x, y, z, qw, qx, qy, qz]` for all superstructure elements (`Robot/Superstructure/3D/*`).
- **Active Game Pieces:** Continue streaming Dyn4j simulated ball objects via `Robot/FuelPoses` as a flat double array of `[x, y, z, qw, qx, qy, qz]` per ball.
- **EKF Covariance Logging:** Broadcast the EKF covariance matrix diagonals (X, Y, Theta) as `Robot/Odometry/Covariance` to enable visualization of odometry error ellipses.

### AdvantageScope Layout JSON Configuration
- **Layout File Output:** Output a customized AdvantageScope layout file named `marvin19_layout.json` to the root directory for easy import.
- **Layout Version Fix:** Explicitly append `"version": "26.0.0"` to the root of the JSON file to circumvent the "file format is not supported" bug in AdvantageScope.
- **Tab Layout Structure:** Include:
  - A 3D Field tab visualizing the robot chassis (`Robot/Pose3d`), Intake (`Robot/Superstructure/3D/Intake`), Cowl (`Robot/Superstructure/3D/Cowl`), Flywheel (`Robot/Superstructure/3D/Flywheel`), and active game pieces (`Robot/FuelPoses`).
  - A Swerve tab visualizing actual vs. target module states (`Robot/SwerveStates`).
  - A Line Graph tab tracking Odometry covariance diagonals and superstructure state variables.

### Claude's Discretion
- Exact 3D offsets for superstructure components relative to the robot's center.
- Custom field visual configurations in the AdvantageScope layout JSON.
- Implementation of the covariance extraction helper in the EKF/odometry layer.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **ARESRobot.kt:** Contains the TimedRobot loop where telemetry is compiled and published.
- **TelemetryPublisher.kt:** Provides direct NT4 struct and topic publishers for simulated environments.
- **FRCTelemetry.kt:** Implements the `ITelemetry` interface for FRC.
- **COORDINATE_GUIDE.md:** Documents the center-origin mapping for the 3D field.

### Established Patterns
- Telemetry variables use standard SmartDashboard paths (`Robot/Odometry/*`, `Robot/Pose3d`, `Robot/SwerveStates`).
- Dyn4j body state translation to 3D array poses (qW, qX, qY, qZ quaternion representation).

### Integration Points
- Telemetry logging block in `ARESRobot.kt` (lines 420-523).
- EKF pose estimator state access to obtain covariance matrix bounds.

</code_context>

<specifics>
## Specific Ideas
- Fix the swerve modules logging angle units from degrees to radians so they display correctly in AdvantageScope.
- Create an automated layout JSON containing the version 26.0.0 marker.

</specifics>

<deferred>
## Deferred Ideas
- None — all telemetry goals align within this phase's visualizer stream bounds.

</deferred>
