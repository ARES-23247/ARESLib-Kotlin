package com.areslib.hardware.vision

import com.areslib.math.Pose3d
import com.areslib.math.Rotation3d
import com.areslib.math.Translation3d
import com.areslib.state.VisionMeasurement

class SimVisionIO(
    override val cameraPoses: List<Pose3d> = listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)))
) : VisionIO {
    override fun updateInputs(inputs: VisionIOInputs) {
        inputs.cameraPoses = cameraPoses
        inputs.isConnected = true
        
        // In a real simulation, we would calculate intersections with field tags.
        // For now, this just acts as an empty source unless manually populated by the sim.
    }

    /**
     * Helper for the simulator to inject fake detections.
     */
    fun injectMeasurement(inputs: VisionIOInputs, x: Double, y: Double, heading: Double, ambiguity: Double = 0.0) {
        val pose = Pose3d(
            Translation3d(x, y, 0.0),
            Rotation3d(0.0, 0.0, heading)
        )
        val measurement = VisionMeasurement(
            timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
            targetPose = pose,
            tagId = 1,
            ambiguity = ambiguity
        )
        inputs.measurements = listOf(measurement)
    }
}
