package com.areslib.hardware.vision

import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d
import com.areslib.math.Translation3d
import com.areslib.math.Rotation3d

import com.areslib.hardware.LoggableDevice

data class VisionIOInputs(
    var isConnected: Boolean = false,
    var measurements: List<VisionMeasurement> = emptyList(),
    var cameraPoses: List<Pose3d> = emptyList()
)

/**
 * Hardware abstraction interface for Vision Subsystems.
 * Can be implemented by Limelight (FTC/FRC) or PhotonVision.
 */
interface VisionIO : LoggableDevice {
    /**
     * The physical mounting offset(s) of the camera(s) relative to the robot center.
     */
    val cameraPoses: List<Pose3d>
        get() = listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)))

    /**
     * Updates the inputs with the latest data from the hardware.
     */
    fun updateInputs(inputs: VisionIOInputs)

    /**
     * Updates the camera with orientation and motion data (used by MegaTag2).
     */
    fun setOrientation(
        yawDegrees: Double, yawRateDegPerSec: Double,
        pitchDegrees: Double, pitchRateDegPerSec: Double,
        rollDegrees: Double, rollRateDegPerSec: Double,
        linearVelocityMps: Double = 0.0
    ) {}
}
