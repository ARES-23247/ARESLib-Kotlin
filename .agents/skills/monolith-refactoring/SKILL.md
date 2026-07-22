---
name: monolith-refactoring
description: Decomposes monolithic classes or god-files into modular single-responsibility controllers using the Facade pattern while preserving 100% backward API compatibility.
---

# Monolith Architectural Refactoring Protocol

This skill guides AI coding agents in decomposing large monolithic classes ("god files") into modular, single-responsibility controllers while preserving 100% backward compatibility for all existing public APIs and zero-GC performance constraints.

---

## 1. Responsibility Audit

When invoked on a target file:
1. Inspect the entire file and identify distinct sub-domains (e.g., state machines, hardware map parsing, kinematics/profiling math, telemetry logging, CLI parsing).
2. Quantify line count and anti-pattern hotspots.

---

## 2. Implementation Plan & Design

Before making code edits:
1. Propose single-responsibility sub-components in logical subpackages (e.g., `calibration/`, `drivetrain/`, `pathing/`, `cli/`, `physics/`).
2. Design a **Facade Pattern** for the original file, reducing line count to ~150–250 lines.
3. Explicitly verify that every public property, method, and constructor argument on the original class is preserved via delegation to prevent breaking dependent code or test suites.

---

## 3. Refactoring Execution Rules

1. **Single Responsibility Principle (SRP)**: Each extracted class must encapsulate exactly one domain concern.
2. **Zero-GC & High-Frequency Loop Constraints**:
   - Maintain pre-allocated buffers, primitive types, and object pools.
   - Do NOT introduce reflection or dynamic heap allocations inside high-frequency `update()` or `step()` loops.
3. **Facade Delegation**:
   - The refactored facade class delegates calls to internal controller instances/singletons.
   - Property getters/setters bridge top-level properties to corresponding sub-controllers.

---

## 4. Verification & Validation

After editing code:
1. Automatically execute compile and unit test commands (e.g., `./gradlew test` or module-specific test commands).
2. If compilation errors occur, inspect logs, resolve missing imports or type mismatches autonomously, and re-run verification until clean success (`BUILD SUCCESSFUL`).
