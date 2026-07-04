package com.areslib.sim
import edu.wpi.first.networktables.NetworkTableInstance
fun test() {
    val ntInst = NetworkTableInstance.getDefault()
    val topic = ntInst.getStringTopic("ARES/Test")
    topic.setRetained(true)
    val pub = topic.publish()
}
