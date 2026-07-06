import edu.wpi.first.networktables.NetworkTableInstance

fun main() {
    val ntInst = NetworkTableInstance.getDefault()
    ntInst.startClient4("ARES-Analytics-Test")
    ntInst.setServer("127.0.0.1")
    
    val sub = ntInst.getDoubleArrayTopic("ARES/EstimatedPose").subscribe(doubleArrayOf())
    
    var count = 0
    while (count < 10) {
        val vals = sub.get()
        if (vals.isNotEmpty()) {
            println("EstimatedPose: ${vals.joinToString()}")
            count++
        }
        Thread.sleep(100)
    }
}

