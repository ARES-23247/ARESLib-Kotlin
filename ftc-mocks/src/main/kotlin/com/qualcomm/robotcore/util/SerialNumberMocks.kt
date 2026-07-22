@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.util

/**
 * Class implementation for Serial Number.
 *
 * Robotics framework control component.
 */
open class SerialNumber(val serialNumber: String = "") {
    val isEmbeddedSerialNumber: Boolean = false
    /**
     * toString declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun toString(): String = serialNumber
    
    companion object {
        @JvmStatic
        fun fromString(serialNumber: String): SerialNumber = SerialNumber(serialNumber)
    }
}
