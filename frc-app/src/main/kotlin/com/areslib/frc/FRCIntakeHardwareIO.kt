package com.areslib.frc

import com.areslib.hardware.IntakeIO
import com.ctre.phoenix6.controls.PositionVoltage
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.CANcoder
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Concrete implementation of IntakeIO utilizing CTRE TalonFX motors for pivot
 * and rollers, along with a CTRE CANcoder absolute position sensor.
 */
class FRCIntakeHardwareIO(
    private val pivotMotor: TalonFX,
    private val rollerMotor: TalonFX,
    private val absoluteEncoder: CANcoder
) : IntakeIO {

    private val positionRequest = PositionVoltage(0.0)
    private val voltageRequest = VoltageOut(0.0)

    init {
        // Apply standard current limits
        val pivotConfig = com.ctre.phoenix6.configs.TalonFXConfiguration()
        pivotConfig.CurrentLimits.StatorCurrentLimit = 35.0
        pivotConfig.CurrentLimits.StatorCurrentLimitEnable = true
        pivotMotor.configurator.apply(pivotConfig)

        val rollerConfig = com.ctre.phoenix6.configs.TalonFXConfiguration()
        rollerConfig.CurrentLimits.StatorCurrentLimit = 30.0
        rollerConfig.CurrentLimits.StatorCurrentLimitEnable = true
        rollerMotor.configurator.apply(rollerConfig)
    }

    override fun setPivotAngle(degrees: Double) {
        // Convert degrees to motor rotations (1 degree = (1.0 / 360.0) rotations)
        // Adjust for any gear ratio (assuming 1:1 absolute feedback configuration or internal CTRE scaling)
        val rotations = degrees / 360.0
        pivotMotor.setControl(positionRequest.withPosition(rotations))
    }

    override fun setPivotVoltage(volts: Double) {
        pivotMotor.setControl(voltageRequest.withOutput(volts))
    }

    override fun setRollerVoltage(volts: Double) {
        rollerMotor.setControl(voltageRequest.withOutput(volts))
    }

    override val pivotAngleDegrees: Double
        get() = absoluteEncoder.absolutePosition.valueAsDouble * 360.0

    override val pivotCurrentAmps: Double
        get() = pivotMotor.statorCurrent.valueAsDouble

    override val rollerCurrentAmps: Double
        get() = rollerMotor.statorCurrent.valueAsDouble
}
