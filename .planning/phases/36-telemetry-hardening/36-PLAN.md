# Phase 36: Telemetry Hardening - Plan

## 1. Harden `NT4Telemetry`
- Wrap `inst.startNT4Server()` in a `try-catch` block.
- Add an `isInitialized` boolean flag to `NT4Telemetry`. If false, bypass all `put*` and `get*` operations.
- Wrap all `put*` and `get*` methods in `try-catch` blocks to swallow any unexpected JNI/socket exceptions.

## 2. Harden `ARESDataLogger`
- Ensure `logDir.mkdirs()` and `File` operations in the `init` block are wrapped in `try-catch`.
- If an exception occurs during file creation, log an error to `System.err` and leave `isRunning = false` so `logFrame` operations return immediately without crashing.

## 3. Verify Compilation
- Ensure `core` unit tests (`gradlew :core:test`) continue to pass.
- Run `gradlew :TeamCode:assembleDebug` to confirm no API breaks.
