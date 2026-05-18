package com.areslib.frc

import com.areslib.hardware.FlywheelIO
import com.ctre.phoenix6.controls.Follower
import com.ctre.phoenix6.controls.VelocityVoltage
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Concrete implementation of FlywheelIO utilizing dual CTRE TalonFX motors
 * in a Leader/Follower layout via Phoenix 6.
 */
class FRCFlywheelHardwareIO(
    private val leader: TalonFX,
    private val follower: TalonFX
) : FlywheelIO {

    private val velocityRequest = VelocityVoltage(0.0)
    private val voltageRequest = VoltageOut(0.0)

    init {
        // Set the follower motor to exactly mirror the leader motor
        follower.setControl(Follower(leader.deviceID, false))
        
        // Enforce basic standard current limiting to protect FRC superstructures
        val config = com.ctre.phoenix6.configs.TalonFXConfiguration()
        config.CurrentLimits.StatorCurrentLimit = 40.0
        config.CurrentLimits.StatorCurrentLimitEnable = true
        leader.configurator.apply(config)
        follower.configurator.apply(config)
    }

    override fun setVelocityRpm(rpm: Double) {
        val rps = rpm / 60.0
        leader.setControl(velocityRequest.withVelocity(rps))
    }

    override fun setAppliedVoltage(volts: Double) {
        leader.setControl(voltageRequest.withOutput(volts))
    }

    override val velocityRpm: Double
        get() = leader.velocity.valueAsDouble * 60.0

    override val currentAmps: Double
        get() = leader.statorCurrent.valueAsDouble + follower.statorCurrent.valueAsDouble

    override val tempCelsius: Double
        get() = leader.deviceTemp.valueAsDouble
}
