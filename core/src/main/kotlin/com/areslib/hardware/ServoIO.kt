package com.areslib.hardware

/**
 * Pure abstraction for reading/writing to a physical servo.
 * Keeps the :core module decoupled from Qualcomm SDK.
 */
interface ServoIO {
    /**
     * Servo position (0.0 to 1.0)
     */
    var position: Double
}
