package com.areslib.ftc

import com.qualcomm.robotcore.hardware.Gamepad
import com.areslib.math.InputMath
import com.areslib.action.RobotAction

class GamepadIO(
    private val gamepad: Gamepad,
    private val deadband: Double = 0.05,
    private val curveExponent: Double = 2.0
) {
    /**
     * Reads raw Gamepad axes and returns a pure DriveIntent action.
     * In FTC, left_stick_y is negative when pushed forward.
     */
    fun getDriveIntent(): RobotAction.JoystickDriveIntent {
        // Read raw axes
        val rawX = gamepad.left_stick_x.toDouble()
        // Invert Y because FTC gamepad Y is negative when pushed up
        val rawY = -gamepad.left_stick_y.toDouble()
        val rawOmega = gamepad.right_stick_x.toDouble()

        // Apply deadband
        val dbX = InputMath.applyDeadband(rawX, deadband)
        val dbY = InputMath.applyDeadband(rawY, deadband)
        val dbOmega = InputMath.applyDeadband(rawOmega, deadband)

        // Apply curve
        val curvedX = InputMath.applyCurve(dbX, curveExponent)
        val curvedY = InputMath.applyCurve(dbY, curveExponent)
        val curvedOmega = InputMath.applyCurve(dbOmega, curveExponent)

        return RobotAction.JoystickDriveIntent(
            targetXVelocity = curvedX,
            targetYVelocity = curvedY,
            targetAngularVelocity = curvedOmega
        )
    }
}
