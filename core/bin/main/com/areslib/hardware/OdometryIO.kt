package com.areslib.hardware

import com.areslib.math.Pose2d

/**
 * Pure abstraction for reading a high-speed odometry module
 * such as the goBILDA Pinpoint or OTOS.
 */
interface OdometryIO {
    /**
     * Initializes the odometry module, resetting its internal position
     * to the given starting pose.
     */
    fun initialize(startPose: Pose2d = Pose2d())
    
    /**
     * Update the odometry module (usually pulls bulk data via I2C/SPI)
     */
    fun update()
    
    /**
     * Current absolute position tracked by the module
     */
    val position: Pose2d
    
    /**
     * Current absolute velocity of the module
     */
    val velocity: Pose2d
}
