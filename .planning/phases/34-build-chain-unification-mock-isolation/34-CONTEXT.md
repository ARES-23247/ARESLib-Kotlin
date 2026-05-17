# Phase 34: Build Chain Unification & Mock Isolation - Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Restructure Gradle modules so `:core` and `:ftc-hardware` are consumable by the Android build pipeline, and isolate mock FTC SDK stubs into a test-only source set.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- None specifically required (build configuration updates only)

### Established Patterns
- Existing multi-project build via `settings.gradle.kts`
- FTC SDK nested build in `ftc-app/`

### Integration Points
- Root Gradle project structure
- `core/build.gradle.kts`
- `ftc-hardware/build.gradle.kts`
- `ftc-app/TeamCode/build.gradle`

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>
