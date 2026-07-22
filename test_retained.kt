package com.areslib.sim
import edu.wpi.first.networktables.NetworkTableInstance
/**
 * test declaration.
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
