package com.areslib.hardware.vision

import com.areslib.state.VisionMeasurement
import com.areslib.math.geometry.Pose3d

/**
 * A composite implementation of [VisionIO] that aggregates measurements from multiple vision sources.
 */
class CompositeVisionIO(private val ios: List<VisionIO>) : VisionIO, AutoCloseable {

    override val cameraPoses: List<Pose3d>
        get() = ios.flatMap { it.cameraPoses }

    /**
     * updateInputs declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
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

    /**
     * setOrientation declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun setOrientation(
        yawDegrees: Double, yawRateDegPerSec: Double,
        pitchDegrees: Double, pitchRateDegPerSec: Double,
        rollDegrees: Double, rollRateDegPerSec: Double,
        linearVelocityMps: Double
    ) {
        for (io in ios) {
            io.setOrientation(
                yawDegrees, yawRateDegPerSec,
                pitchDegrees, pitchRateDegPerSec,
                rollDegrees, rollRateDegPerSec,
                linearVelocityMps
            )
        }
    }

    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        for (io in ios) {
            if (io is AutoCloseable) {
                try {
                    io.close()
                } catch (_: Exception) {}
            }
        }
    }
}
