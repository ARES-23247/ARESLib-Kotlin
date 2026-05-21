# Codebase Audit Plan

## Architectural & Code Quality Audit of ARESLib-Kotlin

The goal is to analyze the repository for violations of Redux purity, GC allocations in hot paths, clock purity, mathematical instability, and hardware safety.

## Planned Milestones
1. **Plan & Initialize**: Set up our orchestrator metadata (BRIEFING, plan, progress, cron).
2. **Comprehensive Codebase Discovery**: Dispatch `teamwork_preview_explorer` to scan all codebase paths and catalog files relevant to:
   - R1: State & Redux purity
   - R2: Zero-GC in hot paths (math/control/pathing)
   - R3: Time-determinism/clock purity
   - R4: Math stability & boundary checks
   - R5: Hardware timeout & thread purity
3. **Deep Analysis & Test Verification**: Dispatch `teamwork_preview_worker` or `teamwork_preview_explorer` to compile the codebase, run `.\gradlew.bat test` to verify current tests, and construct the precise lists of violations for R1-R5.
4. **Report Compilation**: Gather and synthesize findings from explorers/workers into the final structured report at `reports/codebase_audit_report.md`.
5. **Validation and Handoff**: Perform final review and output `handoff.md`.

## Subagent Allocations
- **Explorer 1**: Scan core and modules, identifying architecture patterns and initial issues.
- **Worker 1**: Run tests using `.\gradlew.bat test` and verify compile state; assist in resolving details of code paths.
- **Auditor**: Run independent forensics verification to ensure report integrity.
