package com.areslib.frc

import com.areslib.hardware.FlywheelIO
import com.ctre.phoenix6.controls.Follower
import com.ctre.phoenix6.controls.VelocityVoltage
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Concrete implementation of FlywheelIO utilizing 4 physical CTRE TalonFX motors
 * on the "CAN2" high-speed bus. Geared in opposing pairs.
 */
class FRCFlywheelHardwareIO(
    private val leftMaster: TalonFX,
    private val leftFollower: TalonFX,
    private val rightMaster: TalonFX,
    private val rightFollower: TalonFX
) : FlywheelIO {

    private val velocityRequest = VelocityVoltage(0.0)
    private val voltageRequest = VoltageOut(0.0)

    init {
        // Configure followers as opposed to their respective masters
        leftFollower.setControl(Follower(leftMaster.deviceID, true))
        rightFollower.setControl(Follower(rightMaster.deviceID, true))
        
        // Enforce basic standard current limiting to protect FRC superstructures
        val config = com.ctre.phoenix6.configs.TalonFXConfiguration()
        config.CurrentLimits.StatorCurrentLimit = 40.0
        config.CurrentLimits.StatorCurrentLimitEnable = true
        
        leftMaster.configurator.apply(config)
        leftFollower.configurator.apply(config)
        rightMaster.configurator.apply(config)
        rightFollower.configurator.apply(config)
    }

    override fun setVelocityRpm(rpm: Double) {
        val rps = rpm / 60.0
        leftMaster.setControl(velocityRequest.withVelocity(rps))
        rightMaster.setControl(velocityRequest.withVelocity(rps))
    }

    override fun setAppliedVoltage(volts: Double) {
        leftMaster.setControl(voltageRequest.withOutput(volts))
        rightMaster.setControl(voltageRequest.withOutput(volts))
    }

    override val velocityRpm: Double
        get() = (leftMaster.velocity.valueAsDouble + rightMaster.velocity.valueAsDouble) / 2.0 * 60.0

    override val currentAmps: Double
        get() = leftMaster.statorCurrent.valueAsDouble +
                leftFollower.statorCurrent.valueAsDouble +
                rightMaster.statorCurrent.valueAsDouble +
                rightFollower.statorCurrent.valueAsDouble

    override val tempCelsius: Double
        get() = Math.max(leftMaster.deviceTemp.valueAsDouble, rightMaster.deviceTemp.valueAsDouble)
}
