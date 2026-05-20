# BRIEFING

## Mission
Design E2E test cases for Milestone 1 (Math/Control Hardening) per Tiers 1-3.

## 🔒 My Identity
- Archetype: self
- Roles: Sub-orchestrator
- Working directory: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\e2e_testing_orch_m1
- Original parent: e2e_testing_orch
- Original parent conversation ID: 8579553f-f062-4ebe-88ab-e4e2c87f3c74

## 🔒 My Workflow
- **Pattern**: Dual Track - E2E Testing
- **Scope document**: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\e2e_testing_orch_m1\SCOPE.md
- **Iteration loop**: Explorer -> Worker -> Reviewer -> gate.
  Wait, I don't need to explore if I'm just writing tests.
  I will just spawn a Worker to write the tests, run `gradlew test` to ensure it compiles, then Reviewer.
