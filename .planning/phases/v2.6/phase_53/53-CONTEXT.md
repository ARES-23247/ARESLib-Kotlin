# Phase 53: Centripetal Velocity Limiting & Swerve Rate Limiting - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated

<domain>
## Phase Boundary

Implement dynamic physical constraints on robot trajectories and hardware outputs:
1. **Centripetal Velocity Limiting**: Prevent tipping or loss of traction during high-speed curves by calculating curvature $\kappa = d\theta/ds$ and dynamically capping linear velocity: $v \le \sqrt{a_{max} / |\kappa|}$.
2. **Swerve Rate Limiter**: Limit swerve module steering angular acceleration and drive wheel acceleration inside `SwerveKinematics` to prevent actuator saturation.

</domain>

<decisions>
## Implementation Decisions

### Kinematics Rate Limiting
- Add state tracking to `SwerveKinematics` to store the previous modules' states and time.
- Steering speed limit: `maxSteerVelRadPerSec` (default `Math.PI * 4.0`).
- Steering acceleration limit: `maxSteerAccelRadPerSec2` (default `Math.PI * 8.0`).
- Drive acceleration limit: `maxDriveAccelMetersPerSec2` (default `8.0`).
- Time-delta calculation: `dtSeconds`. Cap steering/drive changes relative to this time-delta.

### Curve Centripetal Capping
- Add `curvature` to `PathPoint` or calculate numerically in `Path`.
- Integrate centripetal velocity capping inside `HolonomicDriveController` using $v \le \sqrt{a_{max} / |\kappa|}$.

</decisions>

<code_context>
## Existing Code Insights

- `SwerveKinematics`: Implements module-state conversion but has no state tracking or rate limits.
- `HolonomicDriveController`: Evaluates feedback & feedforward speeds without physical acceleration check or curve speed capping.
- `Path`: Currently sample points without explicit curvature fields.

</code_context>

<specifics>
## Specific Ideas
- Numerical curvature approximation inside `Path.sampleAtDistance`:
  $$\kappa \approx \frac{\text{sample}(s + \delta s).pose.heading - \text{sample}(s).pose.heading}{\delta s}$$
- Apply rate limiters in `SwerveKinematics` during `toSwerveModuleStates`.

</specifics>
