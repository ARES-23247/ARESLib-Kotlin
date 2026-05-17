package com.areslib.hardware

/**
 * Pure abstraction for reading/writing to a physical motor.
 * Keeps the :core module decoupled from Qualcomm SDK.
 */
interface MotorIO {
    /**
     * Motor power (-1.0 to 1.0)
     */
    var power: Double
    
    /**
     * Current motor velocity in ticks per second
     */
    val velocity: Double
    
    /**
     * Current encoder position in ticks
     */
    val position: Double
    
    /**
     * Reset the encoder position to zero
     */
    fun resetEncoder()
}
