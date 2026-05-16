package com.areslib.frc

data class SwerveInputs(
    var drivePositionRads: Double = 0.0,
    var driveVelocityRadsPerSec: Double = 0.0,
    var steerAbsolutePositionRads: Double = 0.0,
    var timestampMs: Long = 0L
)

interface SwerveModuleIO {
    fun updateInputs(inputs: SwerveInputs)
}
