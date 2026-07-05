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
