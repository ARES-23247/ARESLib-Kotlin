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

        // Enforce exact physical configurations matching SystemConstants.java
        val config = com.ctre.phoenix6.configs.TalonFXConfiguration()
        config.Slot0.kP = 0.5
        config.Slot0.kI = 2.0
        config.Slot0.kD = 0.0
        config.Slot0.kV = 0.12 // 12.0 / 100.0 (Max speed: 6000 RPM / 60 = 100 RPS)

        config.MotorOutput.NeutralMode = com.ctre.phoenix6.signals.NeutralModeValue.Coast
        config.MotorOutput.Inverted = com.ctre.phoenix6.signals.InvertedValue.CounterClockwise_Positive

        config.Feedback.SensorToMechanismRatio = 1.0

        config.Voltage.PeakReverseVoltage = 0.0 // Software lock reversal of flywheel
        config.CurrentLimits.SupplyCurrentLimitEnable = true
        config.CurrentLimits.SupplyCurrentLimit = 70.0
        config.CurrentLimits.StatorCurrentLimitEnable = true
        config.CurrentLimits.StatorCurrentLimit = 120.0

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
