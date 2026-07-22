package com.areslib.hardware.actuator

import com.areslib.hardware.SubsystemIO

/**
 * Pure abstraction for reading/writing to a physical servo.
 * Keeps the :core module decoupled from Qualcomm SDK.
 */
interface ServoIO : SubsystemIO {
    /**
     * logTelemetry declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun logTelemetry(telemetry: com.areslib.telemetry.ITelemetry, prefix: String) {
        telemetry.putNumber("$prefix/Position", position)
    }

    /**
     * Servo position (0.0 to 1.0)
     */
    var position: Double
}
