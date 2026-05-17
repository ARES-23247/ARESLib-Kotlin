# Phase 36: Telemetry Hardening - Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Make NT4 telemetry and data logging resilient to missing clients and Control Hub storage paths.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
- `NT4Telemetry`: Add `try-catch` blocks around all NetworkTables interactions and the server start logic to prevent socket binding errors from crashing the robot.
- `ARESDataLogger`: Wrap the initial file creation logic in a `try-catch` to avoid crashes from missing Android storage permissions (`SecurityException`).
</decisions>
