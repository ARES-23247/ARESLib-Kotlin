<!-- GSD:project-start source:PROJECT.md -->
## Project

**ARESLib-Kotlin**

ARESLib-Kotlin is a foundational, cross-platform (FTC and FRC) robotics library built with Kotlin 1.9+. It provides a highly performant, functional, and immutable state-driven core. By leveraging a Redux-style state store and an "IO Layer" pattern, it fully decouples pure control logic (such as Swerve, Mecanum, and Differential kinematics, and PathPlanner trajectory following) from hardware SDKs, ensuring 100% offline testability.

**Core Value:** 100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.

### Constraints

- **Architecture**: Redux-style — State must be immutable and transitioned strictly via Actions and pure Reducers.
- **Garbage Collection**: Android ART GC constraints — Avoid heavy allocations in `opModeIsActive()` or `robotPeriodic()`.
- **Cross-Platform**: Code must build and run on both Android (FTC) and RoboRIO (FRC).
- **Tooling**: Pure data struct serialization for AdvantageScope rather than annotation generation.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:STACK.md -->
## Technology Stack

Technology stack not yet documented. Will populate after codebase mapping or first phase.
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
