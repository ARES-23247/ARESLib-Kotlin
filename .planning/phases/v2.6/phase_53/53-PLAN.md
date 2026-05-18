# Phase 53: Centripetal Velocity Limiting & Swerve Rate Limiting - Plan

Implementation checklist for physical velocity curve capping and actuator-saturation rate limiting.

## Proposed Changes

### Core Kinematics & Controls

#### [MODIFY] [Path.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/pathing/Path.kt)
- Add optional `curvature` field to `PathPoint` (default `0.0`).
- Update trajectory parsing or interpolation to compute curvature dynamically if needed, or allow points to supply it directly.

#### [MODIFY] [HolonomicDriveController.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/control/HolonomicDriveController.kt)
- Accept `curvature` and `maxCentripetalAccel` parameter in `calculate`.
- Apply centripetal velocity cap to target velocity feedforward.

#### [MODIFY] [SwerveKinematics.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/kinematics/SwerveKinematics.kt)
- Add tracking fields: `previousStates: Array<SwerveModuleState>?` and `lastTimeSeconds: Double`.
- Add rate limits fields: `maxSteerVelRadPerSec`, `maxSteerAccelRadPerSec2`, `maxDriveAccelMps2`.
- Apply rate limiting in `toSwerveModuleStates` based on time-delta `dtSeconds`.

### Automated Verification

#### [NEW] [DynamicConstraintsTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/control/DynamicConstraintsTest.kt)
- Test centripetal velocity cap reduces feedforward in high-curvature paths.
- Test `SwerveKinematics` rate-limiting prevents infinite/large steps in speeds & steering angles.
