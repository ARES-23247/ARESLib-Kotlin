package com.areslib.ftc.hardware

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.Servo
import kotlin.math.abs

/**
 * A wrapper for [DcMotorEx] that skips redundant I2C writes.
 * It tracks the last power sent to the motor and only delegates the write if
 * the new power differs by at least [epsilon] or if commanding a hard stop (0.0).
 */
class CachedDcMotorEx(
    private val delegate: DcMotorEx,
    private val epsilon: Double = 0.02
) : DcMotorEx by delegate {

    private var lastPower = -10.0 // Invalid starting power to guarantee the first write

    override var power: Double
        get() = if (lastPower != -10.0) lastPower else delegate.power
        set(value) {
            if (value == 0.0 && lastPower != 0.0) {
                delegate.power = 0.0
                lastPower = 0.0
            } else if (abs(value - lastPower) >= epsilon) {
                delegate.power = value
                lastPower = value
            }
        }
}

/**
 * A wrapper for [Servo] that skips redundant I2C writes.
 * It tracks the last position sent to the servo and only delegates the write if
 * the new position differs by at least [epsilon].
 */
class CachedServo(
    private val delegate: Servo,
    private val epsilon: Double = 0.005
) : Servo by delegate {

    private var lastPosition = -10.0 // Invalid starting position to guarantee the first write

    override var position: Double
        get() = if (lastPosition != -10.0) lastPosition else delegate.position
        set(value) {
            if (abs(value - lastPosition) >= epsilon) {
                delegate.position = value
                lastPosition = value
            }
        }
}
