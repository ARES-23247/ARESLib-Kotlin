# Phase 35: Deployable TeleOp OpMode - Plan

## 1. TeamCode Constants Configuration
- Create `HardwareConstants.kt` in `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/config`.
- Map typical standard FTC configuration names:
  - `fl_motor`, `fr_motor`, `bl_motor`, `br_motor`
  - Ensure standard Mecanum directions (left side reverse, right side forward usually, but let's configure `HardwareConstants` to define them explicitly).

## 2. ARESMecanumTeleOp Implementation
- Create `ARESMecanumTeleOp.kt` in `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes`.
- Add `@TeleOp(name = "ARES Mecanum", group = "ARES")` annotation.
- Extend `LinearOpMode`.
- Implement `waitForStart()` and the Redux loop inside `runOpMode()`.
- Use the actual hardware IO implementations from `:ftc-hardware` like `MecanumHardwareIO` and `GamepadIO`.

## 3. Clean up `core` Mocks
- Remove the placeholder `ARESMecanumTeleOp.kt` from `:core` as it was a stand-in simulation opmode.

## 4. Compile and Verify
- Run `./gradlew :TeamCode:assembleDebug` to confirm `TeamCode` compiles with the new OpMode.
