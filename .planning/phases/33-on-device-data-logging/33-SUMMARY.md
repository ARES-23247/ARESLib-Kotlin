# Phase 33: On-Device Data Logging

**Status:** Complete
**Date:** 2026-05-17

## Changes
- Created `ARESDataLogger` supporting thread-safe, asynchronous local CSV logging by delegating disk writes to a single-threaded background executor.
- Implemented `DataLoggingTelemetry` composite `ITelemetry` wrapper to record frames on loop `update()` calls while passing metrics to network tables.
- Integrated logging and NT4 streaming cleanly inside standard FTC teleoperated loops.
- Created `ARESDataLoggerTest` to fully check asynchronous output and verify file structures on the local JVM.
