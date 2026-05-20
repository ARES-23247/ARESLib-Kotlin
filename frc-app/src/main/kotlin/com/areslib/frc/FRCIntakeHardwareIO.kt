package com.areslib.frc

import com.areslib.hardware.IntakeIO
import com.ctre.phoenix6.controls.PositionVoltage
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Concrete implementation of IntakeIO utilizing CTRE TalonFX motors for pivot
 * and rollers, tracking position via the internal motor encoder.
 *
 * Designed for Marvin 19's CAN2 bus and 4:1 gear feedback ratio (no CANcoder).
 */
class FRCIntakeHardwareIO(
    private val pivotMotor: TalonFX,
    private val rollerMotor: TalonFX
) : IntakeIO {

    private val positionRequest = PositionVoltage(0.0)
    private val voltageRequest = VoltageOut(0.0)

    init {
        // Apply standard current limits and sensor feedback ratio (4:1 mechanism reduction)
        val pivotConfig = com.ctre.phoenix6.configs.TalonFXConfiguration()
        pivotConfig.CurrentLimits.StatorCurrentLimit = 35.0
        pivotConfig.CurrentLimits.StatorCurrentLimitEnable = true
        pivotConfig.Feedback.SensorToMechanismRatio = 4.0 // 4:1 feedback reduction ratio
        pivotMotor.configurator.apply(pivotConfig)

        val rollerConfig = com.ctre.phoenix6.configs.TalonFXConfiguration()
        rollerConfig.CurrentLimits.StatorCurrentLimit = 30.0
        rollerConfig.CurrentLimits.StatorCurrentLimitEnable = true
        rollerMotor.configurator.apply(rollerConfig)
    }

    override fun setPivotAngle(degrees: Double) {
        // Convert degrees to mechanism rotations (1 degree = (1.0 / 360.0) rotations)
        // Feedback.SensorToMechanismRatio handles the internal 4:1 scaling in TalonFX
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
        get() = pivotMotor.position.valueAsDouble * 360.0

    override val pivotCurrentAmps: Double
        get() = pivotMotor.statorCurrent.valueAsDouble

    override val rollerCurrentAmps: Double
        get() = rollerMotor.statorCurrent.valueAsDouble
}
