# Phase 49: State Expansion & IMU Pitch/Roll Telemetry - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

The objective of Phase 49 is to expand the root state tree and action interfaces to track physical inertial parameters: IMU pitch, IMU roll, 3D linear accelerations ($X, Y, Z$), and gyroscopic rate signals. This dynamic data enables tilt safety checks (wheel slippage/beaching guards) and physical shock rejection filters downstream in EKF and outlier modules.

</domain>

<decisions>
## Implementation Decisions

1. **DriveState Field Extensions**:
   - `pitchDegrees: Double = 0.0`
   - `rollDegrees: Double = 0.0`
   - `xAccelerationG: Double = 0.0`
   - `yAccelerationG: Double = 0.0`
   - `zAccelerationG: Double = 0.0`
2. **DriveHardwareUpdate Field Extensions**:
   - `pitchDegrees: Double = 0.0`
   - `rollDegrees: Double = 0.0`
   - `xAccelerationG: Double = 0.0`
   - `yAccelerationG: Double = 0.0`
   - `zAccelerationG: Double = 0.0`
3. **PoseUpdate Field Extensions**:
   - `pitchDegrees: Double = 0.0`
   - `rollDegrees: Double = 0.0`
   - `xAccelerationG: Double = 0.0`
   - `yAccelerationG: Double = 0.0`
   - `zAccelerationG: Double = 0.0`
4. **RootReducer Integration**:
   - Update `rootReducer` to copy over the new telemetry metrics inside `DriveHardwareUpdate` and `PoseUpdate` into the central state database without interrupting kinematics or EKF logic.
5. **AdvantageScope/NT4 Telemetry Support**:
   - Log these expanded parameters in simulation/hardware loggers to ensure real-time visibility in AdvantageScope.

</decisions>

<code_context>
## Existing Code Insights

- `RobotState.kt` defines `DriveState`.
- `RobotAction.kt` defines `DriveHardwareUpdate` and `PoseUpdate`.
- `RootReducer.kt` routes these actions and updates state.
- `FtcDashboardAdapter` / simulation wrappers might need updating to handle new fields if we have mock classes.

</code_context>

<specifics>
## Specific Ideas

- Default acceleration values to $0.0\text{ G}$ or $1.0\text{ G}$ (Z-axis gravity) so we don't break existing tests or simulations that don't supply these parameters.

</specifics>

<deferred>
## Deferred Ideas

- None — all goals fit within state representation.

</deferred>
