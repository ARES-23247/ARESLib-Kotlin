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

/**
 * Defines hardware boundaries for calculating absolute position from analog or PWM sensors.
 * E.g., The REV Through Bore Encoder in absolute mode outputs a pulse width between
 * 1 and 1024 microseconds. V2 specifics can be adjusted here when known.
 */
enum class RevEncoderVersion(val minPulseUs: Double, val maxPulseUs: Double, val maxVoltage: Double) {
    V1(1.0, 1024.0, 3.3),
    V2(1.0, 1024.0, 3.3) // Same defaults for now, adjust when V2 specs are confirmed
}
