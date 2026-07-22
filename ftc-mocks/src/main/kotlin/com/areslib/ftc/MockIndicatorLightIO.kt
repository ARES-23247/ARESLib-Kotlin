package com.areslib.ftc

import com.areslib.hardware.actuator.IndicatorLightColor
import com.areslib.hardware.actuator.IndicatorLightIO
import com.areslib.telemetry.ITelemetry

/**
 * In-memory mock implementation of [IndicatorLightIO] for desktop simulation.
 * Simply stores the current position without any hardware interaction.
 *
 * @param name The logical name of this indicator light (matches the hardware map name).
 */
class MockIndicatorLightIO(val name: String) : IndicatorLightIO {
    override var currentPosition: Double = 0.0
        private set

    /**
     * setPosition declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun setPosition(position: Double) {
        currentPosition = position.coerceIn(0.0, 1.0)
    }

    /**
     * safe declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun safe() {
        setPosition(IndicatorLightColor.OFF.position)
    }

    /**
     * refresh declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun refresh() {
        // Write-only device — no sensor reads needed
    }

    /**
     * logTelemetry declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun logTelemetry(telemetry: ITelemetry, prefix: String) {
        telemetry.putNumber("$prefix/Position", currentPosition)
    }
}
