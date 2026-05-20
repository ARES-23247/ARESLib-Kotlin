package com.areslib.frc

import com.areslib.hardware.ClimberIO
import com.ctre.phoenix6.controls.PositionVoltage
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Concrete implementation of ClimberIO utilizing a CTRE TalonFX motor
 * on ID 19 on the "CAN2" high-speed bus, with configured soft limits.
 */
class FRCClimberHardwareIO(
    private val motor: TalonFX
) : ClimberIO {

    private val positionRequest = PositionVoltage(0.0)
    private val voltageRequest = VoltageOut(0.0)

    // Climber scaling: 20 motor rotations per meter of climber extension
    private val rotationsPerMeter = 20.0

    init {
        val config = com.ctre.phoenix6.configs.TalonFXConfiguration()
        config.CurrentLimits.StatorCurrentLimit = 40.0
        config.CurrentLimits.StatorCurrentLimitEnable = true

        // Configure software soft limits to protect climber elevator
        // Range: 0.0 to 0.6 meters of vertical extension
        config.SoftwareLimitSwitch.ForwardSoftLimitThreshold = 0.6 * rotationsPerMeter
        config.SoftwareLimitSwitch.ForwardSoftLimitEnable = true
        config.SoftwareLimitSwitch.ReverseSoftLimitThreshold = 0.0 * rotationsPerMeter
        config.SoftwareLimitSwitch.ReverseSoftLimitEnable = true

        motor.configurator.apply(config)
    }

    override fun setTargetExtension(meters: Double) {
        val targetRotations = meters * rotationsPerMeter
        motor.setControl(positionRequest.withPosition(targetRotations))
    }

    override fun setAppliedVoltage(volts: Double) {
        motor.setControl(voltageRequest.withOutput(volts))
    }

    override val extensionMeters: Double
        get() = motor.position.valueAsDouble / rotationsPerMeter

    override val currentAmps: Double
        get() = motor.statorCurrent.valueAsDouble
}
