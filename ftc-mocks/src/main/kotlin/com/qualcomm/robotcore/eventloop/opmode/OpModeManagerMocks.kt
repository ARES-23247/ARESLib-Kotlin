@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.robotcore.eventloop.opmode

/**
 * Interface implementation for Op Mode Manager.
 *
 * Robotics framework control component.
 */
interface OpModeManager {
    val activeOpModeName: String
    companion object {
        const val DEFAULT_OP_MODE_NAME = "\$Stop\$Robot\$"
    }
}

/**
 * Interface implementation for Op Mode Manager Notifier.
 *
 * Robotics framework control component.
 */
interface OpModeManagerNotifier {
    /**
     * registerListener declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun registerListener(listener: Notifications)
    /**
     * unregisterListener declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun unregisterListener(listener: Notifications)

    /**
     * Interface implementation for Notifications.
     *
     * Robotics framework control component.
     */
    interface Notifications {
        fun onOpModePreInit(opMode: com.qualcomm.robotcore.eventloop.opmode.OpMode)
        fun onOpModePreStart(opMode: com.qualcomm.robotcore.eventloop.opmode.OpMode)
        fun onOpModePostStop(opMode: com.qualcomm.robotcore.eventloop.opmode.OpMode)
    }
}

/**
 * Class implementation for Op Mode Manager Impl.
 *
 * Robotics framework control component.
 */
open class OpModeManagerImpl : OpModeManager, OpModeManagerNotifier {
    override val activeOpModeName: String = ""
    /**
     * registerListener declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun registerListener(listener: OpModeManagerNotifier.Notifications) {}
    /**
     * unregisterListener declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun unregisterListener(listener: OpModeManagerNotifier.Notifications) {}
}
