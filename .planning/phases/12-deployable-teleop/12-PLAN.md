# Phase 12 Plan: Deployable TeleOp Integration

**Goal:** Provide a complete `LinearOpMode` that glues all IO, State, and Kinematics together.
**Requirements covered:** TELE-01

## 1. Context & Architecture
We now have `MecanumKinematics`, `MecanumHardwareIO`, `PinpointIO`, `GamepadIO`, and the Redux `RootReducer`. To actually drive the robot, a user just needs a standard FTC `LinearOpMode`. We will write `ARESMecanumTeleOp` as a reference implementation that initializes these components and runs the pure control loop.

## 2. Tasks

### [ ] 1. Define `ARESMecanumTeleOp`
- **Files:** `src/main/kotlin/com/areslib/ftc/ARESMecanumTeleOp.kt`
- **Action:** Create `ARESMecanumTeleOp` extending `LinearOpMode`.
    - **Init Phase:** Create `RootReducer`, `MecanumHardwareIO`, `PinpointIO`, and `MecanumKinematics`.
    - **Run Loop Phase:** While `opModeIsActive()`, do:
        1. Read Gamepad via `GamepadIO` -> `JoystickDriveIntent`.
        2. Read Pinpoint via `PinpointIO` -> `PoseUpdate`.
        3. Dispatch actions to Redux store.
        4. Read new `RobotState`.
        5. Convert state to field-centric `ChassisSpeeds`.
        6. Pass `ChassisSpeeds` to `MecanumKinematics` -> `MecanumWheelSpeeds`.
        7. Normalize speeds to max power (e.g., 1.0).
        8. Write `MecanumWheelSpeeds` to `MecanumHardwareIO`.
        9. Send state to dashboard via `FtcDashboardAdapter`.

### [ ] 2. Update Tests (if needed)
- Since `LinearOpMode` is primarily orchestration of side-effects, it's generally not unit-tested directly in FTC, but we can verify it compiles cleanly.

## 3. Review & Verification
- Verify the loop maintains the "read -> reduce -> calculate -> write" pure architecture with no state mutations in the opmode itself.
