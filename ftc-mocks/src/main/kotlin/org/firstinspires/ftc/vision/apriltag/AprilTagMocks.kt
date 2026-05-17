package org.firstinspires.ftc.vision.apriltag

class AprilTagPoseFtc {
    var x: Double = 0.0 // inches
    var y: Double = 0.0 // inches
    var z: Double = 0.0 // inches
    var pitch: Double = 0.0 // degrees
    var roll: Double = 0.0 // degrees
    var yaw: Double = 0.0 // degrees
}

class AprilTagDetection {
    var id: Int = 0
    var ftcPose: AprilTagPoseFtc = AprilTagPoseFtc()
}

open class AprilTagProcessor {
    val freshDetections: List<AprilTagDetection>? = null
}
