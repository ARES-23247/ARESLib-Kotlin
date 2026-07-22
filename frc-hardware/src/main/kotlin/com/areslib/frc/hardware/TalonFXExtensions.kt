package com.areslib.frc.hardware

import com.ctre.phoenix6.BaseStatusSignal
import com.ctre.phoenix6.configs.TalonFXConfiguration
import com.ctre.phoenix6.hardware.TalonFX

/**
 * Batch-applies a configuration block across an iterable collection of TalonFX motor controllers.
 */
fun Iterable<TalonFX>.applyConfig(block: TalonFXConfiguration.() -> Unit) {
    val config = TalonFXConfiguration()
    config.block()
    for (motor in this) {
        motor.configurator.apply(config)
    }
}

/**
 * Batch-sets update frequencies for CTRE Phoenix 6 status signals to optimize CANbus bandwidth.
 */
fun setUpdateFrequencies(hz: Double, vararg signals: BaseStatusSignal) {
    for (signal in signals) {
        signal.setUpdateFrequency(hz)
    }
}
