package com.areslib.frc

import com.areslib.hardware.FeederIO
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX
import edu.wpi.first.wpilibj.DigitalInput

/**
 * Concrete implementation of FeederIO utilizing a CTRE TalonFX motor
 * and a WPILib DigitalInput photo-electric beam break sensor.
 */
class FRCFeederHardwareIO(
    private val motor: TalonFX,
    private val beamBreak: DigitalInput
) : FeederIO {

    private val voltageRequest = VoltageOut(0.0)

    init {
        val config = com.ctre.phoenix6.configs.TalonFXConfiguration()
        config.CurrentLimits.StatorCurrentLimit = 25.0
        config.CurrentLimits.StatorCurrentLimitEnable = true
        motor.configurator.apply(config)
    }

    override fun setAppliedVoltage(volts: Double) {
        motor.setControl(voltageRequest.withOutput(volts))
    }

    override val isBeamBroken: Boolean
        get() = !beamBreak.get() // Active low configuration standard

    override val currentAmps: Double
        get() = motor.statorCurrent.valueAsDouble
}
