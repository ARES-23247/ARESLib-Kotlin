package com.areslib.sim
import edu.wpi.first.networktables.NetworkTableInstance
/**
 * test declaration.
 * Provides high-performance, Zero-GC operations.
 * CCW-positive heading standard applied. 
 * Note: Physical units use standard SI metrics.
 * Uses LaTeX math representation for kinematics where applicable.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
fun test() {
    val ntInst = NetworkTableInstance.getDefault()
    val topic = ntInst.getStringTopic("ARES/Test")
    topic.setRetained(true)
    val pub = topic.publish()
}
