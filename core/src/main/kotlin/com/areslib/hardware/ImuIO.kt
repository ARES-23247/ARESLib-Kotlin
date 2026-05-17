package com.areslib.hardware

import com.areslib.math.Rotation2d

/**
 * Pure abstraction for reading a gyroscope / IMU.
 */
interface ImuIO {
    /**
     * Update the IMU state (e.g. read over I2C)
     */
    fun update()
    
    /**
     * Continuous robot yaw/heading
     */
    val heading: Rotation2d
    
    /**
     * Reset the robot heading to 0 degrees
     */
    fun resetHeading()
}
