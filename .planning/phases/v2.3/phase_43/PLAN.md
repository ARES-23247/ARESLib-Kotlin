# Plan: Phase 43 (Autonomous Path Parsing & State Wiring)

## Focus
Provide robust, thread-safe loading of a PathPlanner autonomous path within the FRC simulation loop (`frc-app`), parse it into dynamic trajectory coordinates, and offset the simulated drive state odometry to match the initial spline point.

## Proposed Changes

### `frc-app` Resources & Assets

#### [NEW] [frc-app/src/main/resources/deploy/pathplanner/paths/SimPath.path](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/frc-app/src/main/resources/deploy/pathplanner/paths/SimPath.path)
- Create a mockup PathPlanner `.path` file containing a 3-waypoint spline (e.g. start at `(2.0, 2.0)`, curve to `(5.0, 4.0)`, end at `(8.0, 2.0)`).

### `frc-app` Code Modifications

#### [NEW] [frc-app/src/main/kotlin/com/areslib/frc/PathLoader.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/frc-app/src/main/kotlin/com/areslib/frc/PathLoader.kt)
- Write a helper utility `PathLoader.loadPath(pathName: String)` to read resources as UTF-8 string streams and dispatch to `PathPlannerParser.parsePath`.

#### [MODIFY] [ARESRobot.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/frc-app/src/main/kotlin/com/areslib/frc/ARESRobot.kt)
- **State Initialization**: Add a `private var activePath: Path? = null` field.
- **`autonomousInit`**: Parse `SimPath` using our `PathLoader`. Extract the very first `PathPoint` and update the Redux state `currentState.drive` with the initial X, Y, and Heading coordinates. Align the `dyn4j` `robotBody` translation/rotation to prevent start-point physics snapping.

## Verification Plan

### Automated Verification
- Run `./gradlew :frc-app:build` to verify successful path resource loading, parsing integration, and compiling.

### Manual Verification
- In the simulation execution, verify in AdvantageScope that `Robot/Odometry/X` and `Robot/Odometry/Y` cleanly snap to the trajectory start point immediately when entering Autonomous mode.
