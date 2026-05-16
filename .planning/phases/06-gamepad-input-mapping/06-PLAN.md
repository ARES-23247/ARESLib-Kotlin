# Phase 6 Plan: Gamepad Input Mapping

**Goal:** Translate raw FTC Gamepad input into pure intent actions.
**Requirements covered:** INPUT-01, INPUT-02

## 1. Context & Architecture
We need to bridge FTC's `Gamepad` class to our pure `RobotAction`s. FTC gamepad axes provide values from -1.0 to 1.0. We need to parse these values, apply deadbands, and optionally apply exponential scaling before dispatching a `RobotAction.DriveIntent`. This logic should be kept out of the `ARESLibOpMode` and encapsulated in an `InputMapper` or similar class to keep the OpMode clean.

## 2. Tasks

### [ ] 1. Define Drive Intent Action
- **Files:** `src/main/kotlin/com/areslib/action/RobotAction.kt`
- **Action:** Ensure `DriveIntent(val x: Double, val y: Double, val omega: Double)` exists. (We have `DriveHardwareUpdate`, but we need a specific human intent action if it doesn't exist).

### [ ] 2. Implement Gamepad Math Utilities
- **Files:** `src/main/kotlin/com/areslib/math/InputMath.kt`
- **Action:** Create pure functions to apply deadbands and exponential curves to joystick inputs.

### [ ] 3. Implement Gamepad IO Bridge
- **Files:** `src/main/kotlin/com/areslib/ftc/GamepadIO.kt`
- **Action:** Create `GamepadIO` class that takes an FTC `Gamepad` instance (mocked earlier), reads the sticks, applies the math, and returns a `RobotAction.DriveIntent`.

### [ ] 4. Unit Tests
- **Files:** `src/test/kotlin/com/areslib/math/InputMathTest.kt`
- **Action:** Verify deadband strips noise, and exponential scaling correctly curves inputs while maintaining the sign.

## 3. Review & Verification
- Verify the math runs without allocating objects.
- Ensure the IO class successfully extracts intent without retaining state.
