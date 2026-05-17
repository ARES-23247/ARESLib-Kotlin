package com.areslib.hardware.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor

class FtcVisionPortalIO(private val aprilTagProcessor: AprilTagProcessor) : VisionIO {

    override fun updateInputs(inputs: VisionIOInputs) {
        val detections = aprilTagProcessor.freshDetections
        
        if (detections != null && detections.isNotEmpty()) {
            inputs.isConnected = true
            
            val measurements = detections.map { detection ->
                val pose = detection.ftcPose
                // VisionPortal returns position in inches and rotation in degrees
                // We convert inches to meters (1 inch = 0.0254 meters)
                val poseMeters = Pose3d(
                    translation = com.areslib.math.Translation3d(
                        x = pose.x * 0.0254,
                        y = pose.y * 0.0254,
                        z = pose.z * 0.0254
                    ),
                    rotation = com.areslib.math.Rotation3d(
                        roll = Math.toRadians(pose.roll),
                        pitch = Math.toRadians(pose.pitch),
                        yaw = Math.toRadians(pose.yaw)
                    )
                )
                
                VisionMeasurement(
                    timestampMs = System.currentTimeMillis(),
                    targetPose = poseMeters,
                    tagId = detection.id,
                    ambiguity = 0.0
                )
            }
            
            inputs.measurements = measurements
        } else {
            inputs.isConnected = detections != null
            inputs.measurements = emptyList()
        }
    }
}
