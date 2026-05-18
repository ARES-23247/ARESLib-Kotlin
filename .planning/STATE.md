---
gsd_state_version: 1.0
milestone: v2.8
milestone_name: Deterministic Input Replay & "What-If" Ghost Simulation
status: Scoping requirements
last_updated: "2026-05-18T16:21:00.000Z"
last_activity: 2026-05-18 — Milestone v2.8 initialized
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-18)

**Core value:** 100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.
**Current focus:** Deterministic Input Replay & "What-If" Ghost Simulation.

## Session Memory

Milestone v2.7 successfully completed path chaining, dynamic tangent Bezier detours, preemptive task stack scheduling, NT4 AdvantageScope/Dashboard streaming, and closed with 100% closed-loop physics simulation coverage.

## Current Position

Phase: Scoping requirements
Plan: —
Status: Scoping requirements
Last activity: 2026-05-18 — Milestone v2.8 initialized

### Current Focus

Scoping requirements for Milestone v2.8 to design platform-agnostic SubsystemIO boundaries, asynchronous sensory input logging, EKF replay runners, and a Kotlin Compose desktop GUI tuning tool for students.

### Next Steps

1. Gather detailed requirements for the IO interfaces, logging serialization format, replay execution timing, grid search optimizer, and Compose desktop layout.
2. Draft `.planning/REQUIREMENTS.md` with explicit, falsifiable requirements.

## Operator Next Steps

- Detail requirements inside .planning/REQUIREMENTS.md
