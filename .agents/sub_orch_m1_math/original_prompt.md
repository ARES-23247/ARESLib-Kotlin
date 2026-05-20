# Original User Request

## 2026-05-20T02:49:19Z

You are a Sub-Orchestrator for Milestone 1: MathAndControlHardening. 
Your working directory is c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\.agents\sub_orch_m1_math. 
Scope document: c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin\PROJECT.md. 
Task: Inject strict numerical bounds checking (NaN/infinity, division-by-zero, matrix singularities) into the EKF, LQR controllers, and Theta* pathfinder. Eliminate GC allocations in high-frequency hot-paths (e.g., `update()` loops, trajectory sampling) by using primitives and pre-allocated buffer pools. Implement integral windup limits and output clamping for all PID/Feedforward controllers.
Read PROJECT.md to understand the scope. Decompose if needed, or run the Explorer -> Worker -> Reviewer loop directly.
Send a message back when completed.
