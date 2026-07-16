# ARESLib-Kotlin Agent Rules

## Limelight Target-Space Coordinate System

When working with `VisionMeasurement.robotPoseTargetSpace` (AprilTag alignment/tracking), the coordinate system and rotation axis mapping is **non-obvious** and has caused bugs. Always reference this section before writing vision alignment code.

### Target-Space Axes (Origin at AprilTag Face)
- **X+**: Right of the tag (when facing the tag)
- **Y+**: Upward (VERTICAL axis — different from FTC field where Z is up!)
- **Z+**: Outward from tag face toward the camera (DEPTH/distance axis)

### ⚠️ CRITICAL: Rotation Axis Mapping

The Limelight SDK's roll/pitch/yaw are passed into `Rotation3d(roll, pitch, yaw)` **without** a coordinate transform in `FtcLimelightIO`. Because target-space has Y-up (not Z-up like FTC field coords), the Euler angles map differently than you'd expect:

| Physical Rotation          | Rotation3d Property | DO NOT confuse with |
|----------------------------|---------------------|---------------------|
| Robot tilting sideways     | `rotation.x`        |                     |
| **Robot heading (yaw)**    | **`rotation.y`**    | `rotation.z` ← WRONG, this is tilt |
| Robot tilting forward/back | `rotation.z`        | This is NOT heading! |

### Correct Usage
```kotlin
// ✅ CORRECT: heading (robot's left/right rotation relative to tag)
val robotYaw = -robotPoseTargetSpace.rotation.y

// ❌ WRONG: this is roll/tilt, NOT heading
val robotYaw = robotPoseTargetSpace.rotation.z
```

### Translation Usage
```kotlin
val distance = kotlin.math.abs(robotPoseTargetSpace.z)    // depth to tag (meters)
val lateralOffset = robotPoseTargetSpace.x                 // left/right offset (meters)
```

### Why the Negation?
The Limelight's Y-axis rotation convention is opposite to the controller's CCW-positive convention. Negate `rotation.y` to get standard CCW-positive heading.

## Build & Deploy (FTC Robot)
- Always use `.\gradlew.bat :TeamCode:assembleDebug` (Windows) to build
- Deploy via ADB: `adb connect 192.168.43.1:5555` then `adb install -r ftc-app\TeamCode\build\outputs\apk\debug\TeamCode-debug.apk`
- The `ftc-app` directory is a **git submodule** — commit changes inside it, not from the parent repo

## Local API Requirements (LogManagerServer)
- Because the robot Control Hub lacks external internet access, do NOT attempt to upload files directly from the robot to the cloud (`aresfirst-portal`).
- Expose endpoints via `LogManagerServer` (port 5002) such as `/api/download` to allow the Desktop Dashboard to pull `.jsonl` files locally over the robot's local Wi-Fi.

---

## Heading Pipeline & Coordinate Convention

The heading convention is **CCW-positive** (math standard: 0° = +X, 90° = +Y). This has been a major source of bugs. Always reference `GEMINI.md § 5` for the full coordinate system documentation.

### ⚠️ CRITICAL: Negation Rules
1. The GoBilda Pinpoint outputs **CCW-positive** heading natively IF mounted right-side up. However, many physical robot mounts place the Pinpoint upside down, causing it to output **CW-positive** natively.
2. `PinpointIO.kt` handles this via `val headingMult = if (isHeadingCcwPositive) 1.0 else -1.0` and multiplies the raw input by it. 
3. **Do NOT add any negations** anywhere else in the pipeline. It is CCW-positive from PinpointIO onwards.
4. In the simulator, `MecanumRobotDouble.updateSensors()` feeds `trueHeadingRad` directly to the mock Pinpoint.

### Simulator State Sync Pitfall
The sim's `DesktopSimLauncher` maintains a local `var state = RobotState()`. This state is **NOT synced** with the OpMode's Redux store for drive data (only superstructure is synced). The `TelemetryPublisher.publishEstimatedPose()` call must use `currentPose` (Dyn4j ground truth), NOT `state.drive.poseEstimator.estimatedPose` (which is always default/zeroed).

### Motor Hardware Names
The physical robot uses `fl`, `fr`, `rl`, `rr` as motor names ("rl" = rear-left, "rr" = rear-right). The MecanumVisualizer in ARES-Analytics must check for BOTH `bl`/`br` AND `rl`/`rr` naming patterns since different robots may use either convention.

### Dashboard Field Canvas Transform (ARES-Analytics)
The FTC field→canvas transform swaps axes:
- `canvasX = -fieldY`, `canvasY = -fieldX`
- Robot icon arrow points RIGHT at zero rotation
- `PathRenderer.kt` applies a `-90°` offset to heading rotation to compensate
- If you change the canvas mapping or robot icon, re-verify the offset

## Kotlin Code Style & Nested If-Else Avoidance
- **Avoid Nested If Statements**: Do not use deeply nested or chained `if-else` blocks for conditional control flow in Kotlin.
- **Prefer `when` Expressions**: Use clean, argument-less `when` expressions to handle multiple condition branches:
  ```kotlin
  when {
      conditionA -> { ... }
      conditionB -> { ... }
      else -> { ... }
  }
  ```
  This is more idiomatic, highly readable, and maintains zero heap allocations (Zero-GC compliance).

## Vision & Kidnapped Robot Recovery
- **FtcVisionTracker.kt**: Do NOT use isInInit flag. The snap triggers ONCE when hasInitializedPoseWithVision is false, and then relies on consecutiveVisionRejections >= 10 for any further snaps during Active Play.
- **Alliance & Field-Centric Drive**: Field-Centric Drive requires BOTH joystickForward and joystickLeft to be inverted if the alliance is BLUE. This must be handled in the OpMode before passing to ieldRelativeDrive.
- **Simulator Alliance State**: The simulator's DesktopSimLauncher.kt teleport ONLY happens on INIT. If you switch the driver station alliance in the dashboard, the simulator MUST actively dispatch SetAlliance to the OpMode's store so the OpMode knows which field-centric direction to use. The dashboard's ARES/Input/isRedAlliance NT4 topic MUST subscribe to 	rue by default so the simulator matches the OpMode's default Red alliance on startup.
