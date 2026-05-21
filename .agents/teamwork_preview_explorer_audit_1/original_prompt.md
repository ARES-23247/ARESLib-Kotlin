## 2026-05-21T09:29:42Z

You are the Codebase Explorer & Auditor.
Working directory: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\teamwork_preview_explorer_audit_1

Objective:
1. Verify that the codebase compiles and all tests pass by executing `.\gradlew.bat test` (on Windows powershell, run it from c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\).
2. Scan and audit the ARESLib-Kotlin codebase according to the following criteria, identifying specific files, lines, and patterns of violations:
   - R1: State Immutability & Redux Purity: Check core/src/main/kotlin/com/areslib/state/ and core/src/main/kotlin/com/areslib/reducer/ (or related files). Search for mutable properties, shared mutable collections, side-effects in reducers, non-functional state mutations.
   - R2: Zero-GC Allocation in Hot-Paths: Check core/src/main/kotlin/com/areslib/control/, core/src/main/kotlin/com/areslib/math/, and core/src/main/kotlin/com/areslib/pathing/. Look for object instantiations, lambda allocations, Iterator generation, boxing, or standard collection allocations in periodic loops or hot math/control methods.
   - R3: Time-Determinism & Clock Purity: Look across core/, ftc-hardware/, and frc-app/ for uses of impure/non-deterministic clocks (like System.currentTimeMillis(), System.nanoTime(), or instant timestamps) instead of the ARES library system clock/timer interface.
   - R4: Math Stability & Boundary Guard Audit: Check control algorithms and filters (EKF, PID, LQR, Theta*). Search for division by zero risk, NaN/Infinite checks, matrix inversion singularity guards, angular wrapping violations, windup limits, and boundary overflows.
   - R5: Hardware Timeout & Thread Purity: Check ftc-hardware/ and frc-app/ for blocking calls, unhandled I2C/UART timeouts, thread blocks, lack of asynchronous/threaded wrapping, or thread safety issues.
3. Write a comprehensive, detailed audit report (analysis.md and/or handoff.md) in your working directory (.agents/teamwork_preview_explorer_audit_1/). Include exact file names, code snippets, line numbers, and impact analyses.
4. When finished, send a message to me (the parent) with the summary of findings and the path to your handoff report. Do NOT write or modify any source code files.
