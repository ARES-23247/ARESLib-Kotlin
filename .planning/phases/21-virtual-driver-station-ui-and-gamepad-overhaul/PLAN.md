# Phase 21: Virtual Driver Station UI and Gamepad Overhaul

## Goal
Revamp the `VirtualDriverStation` UI with a visual gamepad overlay, implement intake toggle logic, and integrate physical gamepad support using `JInput`.

## Approach
1. **UI Redesign**: Overhaul the Swing-based UI in `VirtualDriverStation.kt` to draw a stylized Gamepad. Left Stick (WASD), Right Stick (QE), Right Trigger (Shoot), Left Bumper (Intake).
2. **Intake Toggle Logic**: Modify `VirtualDriverStation.kt` to use a boolean toggle state for the intake, toggled via `SHIFT` or Left Bumper.
3. **Shoot Logic**: Shoot remains a momentary action (fires only while pressed), mapped to `ENTER` or Right Trigger.
4. **Physical Gamepad Support**: Add `JInput` to `simulator/build.gradle.kts`. Add polling thread in `VirtualDriverStation.kt` to read connected gamepads and map physical inputs to the simulation speeds and virtual UI.
