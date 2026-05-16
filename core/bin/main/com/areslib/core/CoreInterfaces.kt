package com.areslib.core

/**
 * HardwareIO is the base interface for hardware interaction.
 * Implementations will be specific to FRC or FTC and will
 * translate raw sensor data into immutable state.
 */
interface HardwareIO {
    fun updateInputs()
}

/**
 * OutputCommand represents an immutable target state or voltage
 * for a specific subsystem.
 */
interface OutputCommand
