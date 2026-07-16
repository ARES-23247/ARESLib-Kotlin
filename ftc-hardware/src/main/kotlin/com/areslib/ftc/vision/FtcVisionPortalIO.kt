package com.areslib.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.geometry.Pose3d
import com.areslib.math.geometry.Translation3d
import com.areslib.math.geometry.Rotation3d
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor

class FtcVisionPortalIO(
    private val aprilTagProcessor: AprilTagProcessor,
    override val cameraPoses: List<Pose3d> = listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)))
) : VisionIO {

    private var lastWarningTime = 0L
    private val measurementsBuffer = ArrayList<VisionMeasurement>(10)

    override fun updateInputs(inputs: VisionIOInputs) {
        inputs.cameraPoses = cameraPoses
        try {
            val detections = aprilTagProcessor.freshDetections
            
            if (detections != null && detections.isNotEmpty()) {
                inputs.isConnected = true
                
                measurementsBuffer.clear()
                for (i in 0 until detections.size) {
                    val detection = detections[i]
                    val pose = detection.ftcPose
                    // VisionPortal returns position in inches and rotation in degrees
                    // We convert inches to meters (1 inch = 0.0254 meters)
                    val poseMeters = Pose3d(
                        translation = com.areslib.math.geometry.Translation3d(
                            x = pose.x * 0.0254,
                            y = pose.y * 0.0254,
                            z = pose.z * 0.0254
                        ),
                        rotation = com.areslib.math.geometry.Rotation3d(
                            roll = Math.toRadians(pose.roll),
                            pitch = Math.toRadians(pose.pitch),
                            yaw = Math.toRadians(pose.yaw)
                        )
                    )
                    
                    measurementsBuffer.add(
                        VisionMeasurement(
                            timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                            targetPose = poseMeters,
                            tagId = detection.id,
                            ambiguity = 0.0
                        )
                    )
                }
                
                // Return a lightweight copy so the Redux action owns the state.
                // We allocate an ArrayList sized perfectly to avoid resizing and map iterator allocations.
                val safeMeasurements = ArrayList<VisionMeasurement>(measurementsBuffer.size)
                for (i in 0 until measurementsBuffer.size) safeMeasurements.add(measurementsBuffer[i])
                inputs.measurements = safeMeasurements
            } else {
                inputs.isConnected = detections != null
                inputs.measurements = emptyList()
            }
        } catch (e: Exception) {
            inputs.isConnected = false
            inputs.measurements = emptyList()
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            if (now - lastWarningTime > 2000L) {
                System.err.println("FtcVisionPortalIO: Error reading fresh AprilTag detections from hardware. Error: ${e.message}")
                lastWarningTime = now
            }
        }
    }
}

