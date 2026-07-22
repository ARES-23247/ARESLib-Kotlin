package com.areslib.control.safety

/**
 * Platform-agnostic brownout protection guard.
 *
 * Monitors battery voltage and applies graduated power scaling to prevent
 * sudden brownout-induced shutdowns. Works identically for FTC and FRC —
 * the platform layer just supplies the voltage reading.
 *
 * ## How it works
 * The guard defines three voltage zones:
 * 1. **Healthy** (above `warningVoltage`): `powerScale = 1.0` — full power
 * 2. **Warning** (between `criticalVoltage` and `warningVoltage`): Linear ramp from 1.0 down to `minPowerScale`
 * 3. **Critical** (below `criticalVoltage`): `powerScale = 0.0` — all motors disabled
 *
 * A hysteresis band prevents oscillation at zone boundaries. Once the voltage
 * recovers above the threshold + hysteresis, the guard releases.
 *
 * ## Usage
 * ```kotlin
 * val guard = BrownoutGuard() // FTC defaults
 * // In your loop:
 * guard.update(batteryVoltage)
 * motor.powerScale = guard.powerScale
 * ```
 *
 * Zero allocations in the hot path.
 */
/**
 * Class implementation for Brownout Guard.
 *
 * Robotics framework control component.
 */
class BrownoutGuard(
    /** Voltage below which graduated power reduction begins (FTC: 10.0V, FRC: 8.5V) */
    val warningVoltage: Double = 10.0,
    /** Voltage below which ALL motor output is disabled (FTC: 7.5V, FRC: 6.8V) */
    val criticalVoltage: Double = 7.5,
    /** Minimum power scale at the warning→critical boundary before full cutoff */
    val minPowerScale: Double = 0.3,
    /** Hysteresis band in volts to prevent oscillation at zone boundaries */
    val hysteresisVoltage: Double = 0.3,
    /** Nominal battery voltage for percentage calculations (FTC: 13.0V, FRC: 12.6V) */
    val nominalVoltage: Double = 13.0
) {
    /** Current computed power scale factor (0.0 to 1.0) */
    var powerScale: Double = 1.0
        private set

    /** Current brownout protection state */
    var state: BrownoutState = BrownoutState.HEALTHY
        private set

    /** Last voltage reading */
    var lastVoltage: Double = nominalVoltage
        private set

    /** Estimated battery percentage (0.0 to 100.0) based on linear discharge curve */
    var batteryPercent: Double = 100.0
        private set

    /** Number of times the guard has entered WARNING or CRITICAL since last reset */
    var tripCount: Int = 0
        private set

    /**
     * Update the brownout guard with the latest battery voltage reading.
     * Call this once per loop iteration. Zero allocations.
     *
     * @param voltage Current battery voltage in volts
     */
    fun update(voltage: Double) {
        // Reject garbage readings
        if (!voltage.isFinite() || voltage < 0.0) return

        lastVoltage = voltage
        val normVolt = if (nominalVoltage > 0.1) nominalVoltage else 13.0
        batteryPercent = ((voltage / normVolt) * 100.0).coerceIn(0.0, 100.0)

        val previousState = state

        // Apply hysteresis-aware state transitions
        state = when (state) {
            BrownoutState.HEALTHY -> when {
                voltage < criticalVoltage -> BrownoutState.CRITICAL
                voltage < warningVoltage -> BrownoutState.WARNING
                else -> BrownoutState.HEALTHY
            }
            BrownoutState.WARNING -> when {
                voltage < criticalVoltage -> BrownoutState.CRITICAL
                voltage > warningVoltage + hysteresisVoltage -> BrownoutState.HEALTHY
                else -> BrownoutState.WARNING
            }
            BrownoutState.CRITICAL -> when {
                // Must recover above critical + hysteresis to leave CRITICAL
                voltage > criticalVoltage + hysteresisVoltage -> BrownoutState.WARNING
                else -> BrownoutState.CRITICAL
            }
        }

        // Count state transitions into WARNING or CRITICAL
        if (state != BrownoutState.HEALTHY && previousState == BrownoutState.HEALTHY) {
            tripCount++
        }

        // Calculate power scale based on current state
        powerScale = when (state) {
            BrownoutState.HEALTHY -> 1.0
            BrownoutState.CRITICAL -> 0.0
            BrownoutState.WARNING -> {
                // Linear interpolation between warningVoltage (1.0) and criticalVoltage (minPowerScale)
                val range = warningVoltage - criticalVoltage
                if (range <= 0.0) {
                    minPowerScale
                } else {
                    val ratio = (voltage - criticalVoltage) / range
                    minPowerScale + ratio * (1.0 - minPowerScale)
                }
            }
        }
    }

    /** Resets trip counter and state (e.g., at match start) */
    fun reset() {
        tripCount = 0
        state = BrownoutState.HEALTHY
        powerScale = 1.0
        lastVoltage = nominalVoltage
        batteryPercent = 100.0
    }

    companion object {
        /** Pre-configured for FTC (12V system, 20A fuse, REV hubs brownout ~7V) */
        fun ftcDefaults(): BrownoutGuard = BrownoutGuard(
            warningVoltage = 10.0,
            criticalVoltage = 7.5,
            minPowerScale = 0.3,
            hysteresisVoltage = 0.4,
            nominalVoltage = 13.0
        )

        /** Pre-configured for FRC (12V system, PDP/PDH, roboRIO brownout at 6.3V) */
        fun frcDefaults(): BrownoutGuard = BrownoutGuard(
            warningVoltage = 8.5,
            criticalVoltage = 6.8,
            minPowerScale = 0.25,
            hysteresisVoltage = 0.4,
            nominalVoltage = 12.6
        )
    }
}

/** Brownout protection state machine states */
enum class BrownoutState {
    /** Battery voltage is healthy — full power allowed */
    HEALTHY,
    /** Battery voltage is sagging — graduated power reduction active */
    WARNING,
    /** Battery voltage is critically low — all motors disabled */
    CRITICAL
}
