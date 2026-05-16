# Phase 9 Plan: FTC Dashboard & Telemetry

**Goal:** Bridge the immutable `RobotState` to FTC Dashboard for live telemetry and odometry drawing.
**Requirements covered:** DASH-01, DASH-02

## 1. Context & Architecture
Unlike WPILib where AdvantageScope uses native `Struct` logging, FTC utilizes `FtcDashboard` (by acmerobotics). FtcDashboard receives telemetry (key-value pairs) and canvas drawings via a Telemetry Packet. We need an adapter that takes a pure `RobotState`, extracts the odometry data, and constructs a `TelemetryPacket` without maintaining any internal mutating state.

## 2. Tasks

### [ ] 1. Mock FTC Dashboard Classes
- **Files:** `src/main/kotlin/com/qualcomm/robotcore/hardware/FtcMocks.kt`
- **Action:** Mock `FtcDashboard`, `TelemetryPacket`, and `Canvas` to allow our code to compile outside the FTC SDK.

### [ ] 2. Implement Dashboard Adapter
- **Files:** `src/main/kotlin/com/areslib/ftc/FtcDashboardAdapter.kt`
- **Action:** Create `FtcDashboardAdapter` with a `drawRobotState` function that takes a `RobotState` and a `TelemetryPacket`, draws the robot's pose on the field canvas, and adds `x`, `y`, `heading` to telemetry.

### [ ] 3. Unit Tests
- **Files:** `src/test/kotlin/com/areslib/ftc/FtcDashboardAdapterTest.kt`
- **Action:** Verify that passing a dummy `RobotState` correctly interacts with the mocked `TelemetryPacket`.

## 3. Review & Verification
- Verify `FtcDashboardAdapter` does not modify the `RobotState` and purely translates state to telemetry packets.
