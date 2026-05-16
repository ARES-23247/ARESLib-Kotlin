# Phase 7 Plan: Hardware Odometry Bridge

**Goal:** Establish hardware odometry bridging (Pinpoint) and software forward kinematics.
**Requirements covered:** ODOM-01, ODOM-02

## 1. Context & Architecture
To support field-centric drive, we need to know the robot's pose. We want to support both hardware odometry (like the goBILDA Pinpoint or OTOS, which calculates pose on a coprocessor) and standard software odometry (calculated via forward kinematics on the Control Hub). Both sources will dispatch a pure `PoseUpdate` action, meaning the core logic doesn't care where the pose came from.

## 2. Tasks

### [ ] 1. Define Pose Update Action
- **Files:** `src/main/kotlin/com/areslib/action/RobotAction.kt`
- **Action:** Add `PoseUpdate(val x: Double, val y: Double, val headingRadians: Double, val timestampMs: Long)` to `RobotAction`.

### [ ] 2. Implement Hardware Odometry IO (Pinpoint)
- **Files:** `src/main/kotlin/com/areslib/ftc/PinpointIO.kt`, `src/main/kotlin/com/qualcomm/robotcore/hardware/FtcMocks.kt`
- **Action:** Mock a `GoBildaPinpointDriver` in FtcMocks. Implement `PinpointIO` that polls the driver and returns a `RobotAction.PoseUpdate`.

### [ ] 3. Implement Software Forward Kinematics
- **Files:** `src/main/kotlin/com/areslib/math/OdometryMath.kt`
- **Action:** Implement standard dead-wheel / mecanum forward kinematics math (simplified mock for the sake of the structural pipeline) to prove the alternative path.

### [ ] 4. Unit Tests
- **Files:** `src/test/kotlin/com/areslib/math/OdometryMathTest.kt`
- **Action:** Ensure the software forward kinematics yields correct delta poses.

## 3. Review & Verification
- Verify `PinpointIO` performs no internal math and correctly wraps the sensor data into immutable actions.
