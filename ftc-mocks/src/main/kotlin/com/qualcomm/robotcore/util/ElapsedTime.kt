package com.qualcomm.robotcore.util

/**
 * Mock representation of an FTC [ElapsedTime] that integrates with our virtual clock.
 */
open class ElapsedTime {
    companion object {
        private val robotClockClass: Class<*>? = try {
            Class.forName("com.areslib.util.RobotClock")
        } catch (_: ClassNotFoundException) {
            null
        }

        private val currentTimeMillisMethod = try {
            robotClockClass?.getMethod("currentTimeMillis")
        } catch (_: Exception) {
            null
        }

        private fun getVirtualTimeMs(): Long {
            return if (currentTimeMillisMethod != null) {
                try {
                    currentTimeMillisMethod.invoke(null) as Long
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }
        }
    }

    private var startTime: Long = getVirtualTimeMs()

    /**
     * reset declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun reset() {
        startTime = getVirtualTimeMs()
    }

    /**
     * seconds declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun seconds(): Double = (getVirtualTimeMs() - startTime) / 1000.0
    /**
     * milliseconds declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun milliseconds(): Double = (getVirtualTimeMs() - startTime).toDouble()
}
