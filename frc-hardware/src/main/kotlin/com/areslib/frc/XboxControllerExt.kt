package com.areslib.frc

import com.areslib.telemetry.GamepadState
import edu.wpi.first.wpilibj.XboxController

/**
 * Converts a WPILib XboxController into a platform-agnostic GamepadState
 * for the ARESLib unified logging pipeline.
 */
fun XboxController.toState() = GamepadState(
    leftStickX = leftX.toFloat(),
    leftStickY = leftY.toFloat(),
    rightStickX = rightX.toFloat(),
    rightStickY = rightY.toFloat(),
    leftTrigger = leftTriggerAxis.toFloat(),
    rightTrigger = rightTriggerAxis.toFloat(),
    a = aButton,
    b = bButton,
    x = xButton,
    y = yButton,
    dpadUp = pov == 0,
    dpadDown = pov == 180,
    dpadLeft = pov == 270,
    dpadRight = pov == 90,
    leftBumper = leftBumperButton,
    rightBumper = rightBumperButton,
    leftStickButton = leftStickButton,
    rightStickButton = rightStickButton,
    start = startButton,
    back = backButton
)

/**
 * XboxController declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
fun XboxController.updateState(state: GamepadState) {
    if (!edu.wpi.first.wpilibj.DriverStation.isJoystickConnected(this.port)) {
        state.reset()
        return
    }
    state.leftStickX = leftX.toFloat()
    state.leftStickY = leftY.toFloat()
    state.rightStickX = rightX.toFloat()
    state.rightStickY = rightY.toFloat()
    state.leftTrigger = leftTriggerAxis.toFloat()
    state.rightTrigger = rightTriggerAxis.toFloat()
    state.a = aButton
    state.b = bButton
    state.x = xButton
    state.y = yButton
    val currentPov = pov
    state.dpadUp = currentPov == 0
    state.dpadDown = currentPov == 180
    state.dpadLeft = currentPov == 270
    state.dpadRight = currentPov == 90
    state.leftBumper = leftBumperButton
    state.rightBumper = rightBumperButton
    state.leftStickButton = leftStickButton
    state.rightStickButton = rightStickButton
    state.start = startButton
    state.back = backButton
}
