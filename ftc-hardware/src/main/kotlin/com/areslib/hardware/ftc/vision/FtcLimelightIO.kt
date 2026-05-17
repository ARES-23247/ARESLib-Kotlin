package com.areslib.hardware.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d
import com.qualcomm.hardware.limelightvision.Limelight3A

class FtcLimelightIO(private val limelight: Limelight3A) : VisionIO {
    
    init {
        limelight.start()
    }

    override fun updateInputs(inputs: VisionIOInputs) {
        val result = limelight.getLatestResult()
        
        inputs.isConnected = result != null
        
        if (result != null && result.botpose.size >= 6) {
            // botpose: [x, y, z, roll, pitch, yaw] in meters and degrees
            val pose = Pose3d(
                translation = com.areslib.math.Translation3d(
                    x = result.botpose[0],
                    y = result.botpose[1],
                    z = result.botpose[2]
                ),
                rotation = com.areslib.math.Rotation3d(
                    roll = Math.toRadians(result.botpose[3]),
                    pitch = Math.toRadians(result.botpose[4]),
                    yaw = Math.toRadians(result.botpose[5])
                )
            )
            
            val measurement = VisionMeasurement(
                timestampMs = System.currentTimeMillis(),
                targetPose = pose,
                tagId = -1, // Limelight result might give primary tag, but we mock -1 for now
                ambiguity = 0.0 // Could map from result.ta
            )
            
            inputs.measurements = listOf(measurement)
        } else {
            inputs.measurements = emptyList()
        }
    }
}
