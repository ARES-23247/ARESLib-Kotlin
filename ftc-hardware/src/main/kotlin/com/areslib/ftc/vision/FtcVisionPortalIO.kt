package com.areslib.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.geometry.Pose3d
import com.areslib.math.geometry.Translation3d
import com.areslib.math.geometry.Rotation3d
import com.areslib.math.geometry.transformBy
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
                            Math.toRadians(pose.roll),
                            Math.toRadians(pose.pitch),
                            Math.toRadians(pose.yaw)
                        )
                    )
                    
                    val tagConfig = com.areslib.state.RobotFieldManager.activeConfig.apriltags.find { it.id == detection.id }
                    if (tagConfig != null) {
                        val tagFieldPose = Pose3d(
                            com.areslib.math.geometry.Translation3d(tagConfig.x, tagConfig.y, tagConfig.z),
                            com.areslib.math.geometry.Rotation3d(0.0, 0.0, Math.toRadians(tagConfig.yaw))
                        )
                        val cameraToTag = com.areslib.math.geometry.Transform3d(poseMeters.translation, poseMeters.rotation)
                        val robotToCamera = com.areslib.math.geometry.Transform3d(cameraPoses[0].translation, cameraPoses[0].rotation)
                        
                        val absoluteRobotPose = tagFieldPose.transformBy(cameraToTag.inverse()).transformBy(robotToCamera.inverse())
                        
                        measurementsBuffer.add(
                            VisionMeasurement(
                                timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                                targetPose = absoluteRobotPose,
                                tagId = detection.id,
                                ambiguity = 0.0,
                                robotPoseTargetSpace = poseMeters
                            )
                        )
                    }
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

