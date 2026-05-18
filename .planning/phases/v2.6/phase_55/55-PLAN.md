# Phase 55: VFH+ Obstacle Avoidance Detours & Simulator Verification - Plan

Implementation checklist for VFH+ obstacle avoidance detours and simulator validation.

## Proposed Changes

### Core Pathing & Control

#### [NEW] [VFHPlanner.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/pathing/VFHPlanner.kt)
- Create `VFHPlanner` with configurable sector sizes (default 36 sectors), smoothing factors, and obstacle density thresholds.
- Implement `computeDetourHeading(robotPose: Pose2d, targetHeading: Double, obstacles: List<Obstacle>): Double` returning the safe detour heading angle.

#### [MODIFY] [HolonomicDriveController.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/control/HolonomicDriveController.kt)
- Accept `obstacles: List<Obstacle>` inside the feedback update loop.
- If obstacles are detected in the robot's immediate proximity (e.g., $< 1.5\text{m}$), pass the target direction and active costmap obstacles to `VFHPlanner`.
- Blend original feedforward path vectors with VFH+ detour directions to navigate around threats seamlessly.

### Verification Plan

#### [NEW] [VFHPlannerTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/pathing/VFHPlannerTest.kt)
- Test sector density weight accumulations.
- Test 3-point smoothing filter.
- Test valley selection outputs closest heading detour angle.

#### [NEW] [ClosedLoopAvoidanceSimTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/control/ClosedLoopAvoidanceSimTest.kt)
- Run closed-loop simulated drive run towards target point.
- Place a static obstacle directly on the path.
- Verify that the simulated robot successfully avoids the obstacle and completes the path safely.
