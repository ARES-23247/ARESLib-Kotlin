package com.areslib.frc

import com.areslib.hardware.CowlIO
import com.ctre.phoenix6.controls.PositionVoltage
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Concrete implementation of CowlIO utilizing a single CTRE TalonFX motor
 * to actuate the adjustable hood angle.
 */
class FRCCowlHardwareIO(
    private val motor: TalonFX
) : CowlIO {

    private val positionRequest = PositionVoltage(0.0)
    private val voltageRequest = VoltageOut(0.0)

    init {
        val config = com.ctre.phoenix6.configs.TalonFXConfiguration()
        config.CurrentLimits.StatorCurrentLimit = 25.0
        config.CurrentLimits.StatorCurrentLimitEnable = true
        motor.configurator.apply(config)
    }

    override fun setTargetAngle(degrees: Double) {
        val rotations = degrees / 360.0
        motor.setControl(positionRequest.withPosition(rotations))
    }

    override fun setAppliedVoltage(volts: Double) {
        motor.setControl(voltageRequest.withOutput(volts))
    }

    override val angleDegrees: Double
        get() = motor.position.valueAsDouble * 360.0

    override val currentAmps: Double
        get() = motor.statorCurrent.valueAsDouble
}
