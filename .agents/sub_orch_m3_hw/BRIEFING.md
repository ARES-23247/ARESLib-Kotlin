# BRIEFING - Milestone 3 Hardware Fault Tolerance

## Mission
Implement read timeouts and fallback logic for I2C/UART sensors (Pinpoint, Gyro) to prevent loop hangs. Add automated motor current spike limits and stall detection.

## 🔒 My Identity
- Archetype: sub_orch
- Roles: orchestrator, user_liaison, human_reporter, successor
- Working directory: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\sub_orch_m3_hw
- Original parent: 07a26dc2-83f8-471d-998d-184e5216a470
- Original parent conversation ID: 07a26dc2-83f8-471d-998d-184e5216a470

## 🔒 My Workflow
- **Pattern**: Iteration Loop (Explorer -> Worker -> Reviewer)
- **Scope document**: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\sub_orch_m3_hw\SCOPE.md
1. **Decompose**: Single milestone, already decomposed in SCOPE.md
2. **Dispatch & Execute**:
   - **Direct (iteration loop)**: Spawn 1 Explorer to plan the implementation, then 1 Worker, then 2 Reviewers.
3. **On failure**:
   - Retry, Replace, Skip, Redistribute, Redesign, Escalate
4. **Succession**: N/A
- **Work items**:
  1. Milestone 1 [in-progress]
- **Current phase**: 2
- **Current focus**: Milestone 1

## 🔒 Key Constraints
- Never reuse a subagent after handoff.
- Use explicit paths in dispatch.

## Current Parent
- Conversation ID: 07a26dc2-83f8-471d-998d-184e5216a470
- Updated: not yet

## Key Decisions Made
- Decomposed to a single iteration loop.
- Using Explorer to figure out best way to implement timeouts on Android FTC (since I2C hangs are common and require specific mitigation like threading or Future timeouts) and motor stall detection.

## Team Roster
| Agent | Type | Work Item | Status | Conv ID |
|-------|------|-----------|--------|---------|

## Succession Status
- Succession required: no
- Spawn count: 0 / 16
- Pending subagents: none
- Predecessor: none
- Successor: none

## Active Timers
- Heartbeat cron: not started
- Safety timer: none

## Artifact Index
- SCOPE.md - Milestone definitions
