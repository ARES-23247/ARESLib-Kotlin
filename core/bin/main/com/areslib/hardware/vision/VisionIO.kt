package com.areslib.hardware.vision

import com.areslib.state.VisionMeasurement

data class VisionIOInputs(
    var isConnected: Boolean = false,
    var measurements: List<VisionMeasurement> = emptyList()
)

/**
 * Hardware abstraction interface for Vision Subsystems.
 * Can be implemented by Limelight (FTC/FRC) or PhotonVision.
 */
interface VisionIO {
    /**
     * Updates the inputs with the latest data from the hardware.
     */
    fun updateInputs(inputs: VisionIOInputs)
}
