# Phase 56: Multi-Path Chaining & Dynamic Trajectory Switching - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 56-Multi-Path Chaining & Dynamic Trajectory Switching
**Areas discussed:** Trajectory Stitching & Chaining Strategy, Dynamic Detour Trajectory Interception, Path Switching Trigger API, Distance Cumulative Modeling

---

## Trajectory Stitching & Chaining Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Smooth Blend | Blend velocity and interpolate orientation smoothly over a defined overlap region at joint boundaries. | ✓ |
| Hard Stop | Hard-stop execution where the robot comes to a complete halt at the end of the first path before starting the next path. | |

**User's choice:** Smooth Blend (recommended default in --auto mode)
**Notes:** Minimizes high-g shocks and keeps robot momentum continuous during transition.

---

## Dynamic Detour Trajectory Interception

| Option | Description | Selected |
|--------|-------------|----------|
| Immediate Tangent Arc | Calculate a tangent arc from current real-time state directly to detour path start to prevent snaps. | ✓ |
| Deferred Waypoint | Complete the current trajectory segment to the next waypoint before switching to detour. | |

**User's choice:** Immediate Tangent Arc (recommended default in --auto mode)
**Notes:** Guarantees instantaneous, collision-free avoidance maneuver.

---

## Path Switching Trigger API

| Option | Description | Selected |
|--------|-------------|----------|
| Centralized Redux | Dispatch SwitchPathAction through central store to keep state deterministic and reproducible. | ✓ |
| Controller-Level Intercept | Directly intercept within local HolonomicDriveController to update tracking target instantly. | |

**User's choice:** Centralized Redux (recommended default in --auto mode)
**Notes:** Facilitates complete logging, simulation re-run consistency, and telemetry tracking.

---

## Distance Cumulative Modeling

| Option | Description | Selected |
|--------|-------------|----------|
| Continuous Cumulative | Merge multiple segment distances into a single timeline by shifting subsequent path point distances. | ✓ |
| Per-Segment Reset | Reset distance to 0.0 at each path boundary, tracking progress independently per segment. | |

**User's choice:** Continuous Cumulative (recommended default in --auto mode)
**Notes:** Allows standard sampleAtDistance calls across the entire chained path seamlessly.

---

## Claude's Discretion
- Choice of transition blend window size (defaulting to 0.1–0.3 meters).
- Tuning of spline tangents and tangent arc calculation bounds.

## Deferred Ideas
- **CHAIN-03 (Dynamic Spline Fit)**: Real-time generation of multi-point cubic/quintic splines directly on-device (deferred to v3.0).
