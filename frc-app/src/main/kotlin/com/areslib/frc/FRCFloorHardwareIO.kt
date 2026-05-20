package com.areslib.frc

import com.areslib.hardware.FloorIO
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Concrete implementation of FloorIO utilizing a CTRE TalonFX motor
 * on ID 16 on the "CAN2" high-speed bus.
 */
class FRCFloorHardwareIO(
    private val motor: TalonFX
) : FloorIO {

    private val voltageRequest = VoltageOut(0.0)

    init {
        val config = com.ctre.phoenix6.configs.TalonFXConfiguration()
        config.CurrentLimits.StatorCurrentLimit = 30.0
        config.CurrentLimits.StatorCurrentLimitEnable = true
        motor.configurator.apply(config)
    }

    override fun setAppliedVoltage(volts: Double) {
        motor.setControl(voltageRequest.withOutput(volts))
    }

    override val velocityRps: Double
        get() = motor.velocity.valueAsDouble

    override val currentAmps: Double
        get() = motor.statorCurrent.valueAsDouble
}
