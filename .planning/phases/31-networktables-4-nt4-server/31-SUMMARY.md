# Phase 31: NetworkTables 4 (NT4) Server

**Status:** Complete
**Date:** 2026-05-17

## Changes
- Integrated `nt-self-impl` into `:core` to support pure-Kotlin NetworkTables 4 server without standard JNI/RoboRIO dependencies.
- Defined high-level `ITelemetry` interface to wrap telemetry read/write commands.
- Implemented `NT4Telemetry` starting a local NT4 WebSocket server on port 5810, supporting standard robot coordinate array formats for AdvantageScope pose visualization.
- Migrated drivetrain TeleOps to NT4 server streaming and decoupled legacy FTC Dashboard endpoints.
