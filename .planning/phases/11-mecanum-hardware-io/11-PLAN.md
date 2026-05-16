# Phase 11 Plan: Mecanum Hardware IO

**Goal:** Create a hardware bridge that applies calculated `MecanumWheelSpeeds` to real FTC `DcMotorEx` objects.
**Requirements covered:** MEC-02

## 1. Context & Architecture
In our "Airlock" pattern, the hardware IO layer is a dumb terminal. It takes the output of the pure kinematics math and physically applies it to the SDK's motors. It retains no state.

## 2. Tasks

### [ ] 1. Mock `DcMotorEx` and `HardwareMap` (if not already done)
- **Files:** `src/main/kotlin/com/qualcomm/robotcore/hardware/FtcMocks.kt`
- **Action:** Add mock `DcMotorEx` to support hardware calls (e.g., `setPower`, `setDirection`).

### [ ] 2. Implement `MecanumHardwareIO`
- **Files:** `src/main/kotlin/com/areslib/ftc/MecanumHardwareIO.kt`
- **Action:** Create `MecanumHardwareIO(val hardwareMap: HardwareMap)`. In `init()`, get the 4 motors and configure directions. Create an `apply(speeds: MecanumWheelSpeeds)` method that simply calls `.power = ...` on the four motors.

### [ ] 3. Unit Tests
- **Files:** `src/test/kotlin/com/areslib/ftc/MecanumHardwareIOTest.kt`
- **Action:** Verify that passing `MecanumWheelSpeeds` translates directly into corresponding power commands on the mocks.

## 3. Review & Verification
- Verify that `MecanumHardwareIO` has zero internal logic beyond applying scale factors (if necessary) to convert "meters per second" into "-1.0 to 1.0" power ranges or RPM. We will use a simple max speed constant to scale power.
