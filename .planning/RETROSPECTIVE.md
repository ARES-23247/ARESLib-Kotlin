# Project Retrospective

## Milestone: v1.0 — MVP

**Shipped:** 2026-05-16
**Phases:** 5 | **Plans:** 5

### What Was Built
1. Functional Core Scaffold (Pure reducer state boundaries)
2. FRC Bridge (CTRE CANivore integration, AdvantageScope logging)
3. FTC Bridge (Hollow LinearOpMode wrapper)
4. Kinematics Engines (Pure Holonomic/Differential logic)
5. Functional Autonomy (PathPlanner JSON parsing & HolonomicDriveController)

### What Worked
- Complete decoupling from WPILib/FTC SDK resulted in incredibly fast tests.
- Value classes and primitive unrolling kept allocations near zero, satisfying Android ART limitations.

### What Was Inefficient
- Initial hesitation on JSON parsing abstraction without `Jackson` added friction.
- Odometry timestamp synchronization for FRC required some complex `waitForAll` blocking.

### Patterns Established
- "Airlock Pattern": Hardware layers run on their own threads and drop primitive representations into a concurrent queue.
- Pure Reducer Architecture: The entire robot control loop is just `rootReducer(currentState, action)`.

### Key Lessons
- Immutability does not imply performance penalty if data structures are flattened.
- Cross-platform control logic is highly viable when hardware interaction is abstracted to an pure IO interface.
