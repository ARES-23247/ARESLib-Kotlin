package com.areslib.hardware.vision

import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d

/**
 * A composite implementation of [VisionIO] that aggregates measurements from multiple vision sources.
 */
class CompositeVisionIO(private val ios: List<VisionIO>) : VisionIO {

    override val cameraPoses: List<Pose3d>
        get() = ios.flatMap { it.cameraPoses }

    override fun updateInputs(inputs: VisionIOInputs) {
        val allMeasurements = mutableListOf<VisionMeasurement>()
        var anyConnected = false
        
        for (io in ios) {
            val subInputs = VisionIOInputs()
            io.updateInputs(subInputs)
            allMeasurements.addAll(subInputs.measurements)
            if (subInputs.isConnected) {
                anyConnected = true
            }
        }
        
        inputs.isConnected = anyConnected
        inputs.measurements = allMeasurements
        inputs.cameraPoses = cameraPoses
    }
}
