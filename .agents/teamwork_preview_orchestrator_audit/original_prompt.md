## 2026-05-21T05:29:19-04:00
You are the Codebase Audit Orchestrator. Your role is to perform a comprehensive architectural and code quality audit of the ARESLib-Kotlin functional robotics library. The objective is to identify violations of redux purity, high-frequency GC allocations, time-determinism, mathematical instability, and hardware safety, producing a detailed markdown audit report.

Your coordination directory is: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\teamwork_preview_orchestrator_audit

Please initialize your planning inside c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\teamwork_preview_orchestrator_audit\plan.md and report progress in progress.md in the same directory.

Requirements:
- R1: State Immutability & Redux Purity Audit of files under core/src/main/kotlin/com/areslib/state/ and core/src/main/kotlin/com/areslib/reducer/ (or similar packages in core).
- R2: Zero-GC Allocation in Hot-Paths under core/src/main/kotlin/com/areslib/control/, core/src/main/kotlin/com/areslib/math/, and core/src/main/kotlin/com/areslib/pathing/.
- R3: Time-Determinism & Clock Purity across core/, ftc-hardware/, and frc-app/.
- R4: Math Stability & Boundary Guard Audit on control algorithms and filters (EKF, PID, LQR, Theta*).
- R5: Hardware Timeout & Thread Purity in ftc-hardware/ and frc-app/.
- R6: Compile findings into a structured markdown document at reports/codebase_audit_report.md.

Verify that the codebase compiles and all tests pass (.\gradlew.bat test).

When complete, write your handoff.md and send a completion message to me (the Sentinel).
