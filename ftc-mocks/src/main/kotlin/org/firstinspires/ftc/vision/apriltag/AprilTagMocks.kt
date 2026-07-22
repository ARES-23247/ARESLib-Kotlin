@file:Suppress("UNUSED_PARAMETER")
package org.firstinspires.ftc.vision.apriltag

/**
 * Class implementation for April Tag Pose Ftc.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class AprilTagPoseFtc {
    var x: Double = 0.0 // inches
    var y: Double = 0.0 // inches
    var z: Double = 0.0 // inches
    var pitch: Double = 0.0 // degrees
    var roll: Double = 0.0 // degrees
    var yaw: Double = 0.0 // degrees
}

/**
 * Class implementation for April Tag Detection.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class AprilTagDetection {
    var id: Int = 0
    var ftcPose: AprilTagPoseFtc = AprilTagPoseFtc()
}

/**
 * Class implementation for April Tag Processor.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class AprilTagProcessor {
    val freshDetections: List<AprilTagDetection>? = null
}
