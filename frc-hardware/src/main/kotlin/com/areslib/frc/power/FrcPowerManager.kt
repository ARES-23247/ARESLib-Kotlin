package com.areslib.frc.power

import com.areslib.control.safety.BrownoutGuard
import com.areslib.subsystem.PowerManager

/**
 * FRC implementation of the [PowerManager] interface.
 *
 * Manages battery voltage sag filtering and brownout protection for FRC motors.
 * Voltage is acquired through a configurable [batteryVoltageSupplier] (typically
 * wired to `RobotController.getBatteryVoltage()` on the RoboRIO).
 */
class FrcPowerManager : PowerManager {
    /** FRC brownout protection guard */
    val brownoutGuard = BrownoutGuard.frcDefaults()

    /** Supplier for the battery voltage, configurable by the robot platform */
    var batteryVoltageSupplier: () -> Double = { 12.6 }

    override var batteryVoltage = 12.6
        private set
    override var powerScale = 1.0
        private set

    /**
     * Total robot current draw in amperes, estimated from all registered motors.
     */
    override val currentAmps: Double
        get() = com.areslib.hardware.HardwareRegistry.getRegisteredMotors().sumOf { it.currentAmps }

    /**
     * Updates the battery voltage and brownout guard status.
     *
     * @param dtSeconds Loop cycle delta time in seconds (unused — FRC voltage reads via supplier).
     * @param timestampMs Current timestamp from [com.areslib.util.RobotClock] (unused).
     * @return The calculated power scale factor (0.0 to 1.0).
     */
    override fun update(dtSeconds: Double, timestampMs: Long): Double {
        batteryVoltage = batteryVoltageSupplier()
        brownoutGuard.update(batteryVoltage)
        powerScale = brownoutGuard.powerScale

        // Dynamically distribute powerScale to all registered motors
        val motors = com.areslib.hardware.HardwareRegistry.getRegisteredMotors()
        for (m in motors) {
            m.powerScale = powerScale
        }

        return powerScale
    }
}
