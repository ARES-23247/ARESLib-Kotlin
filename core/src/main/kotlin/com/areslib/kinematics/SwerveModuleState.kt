package com.areslib.kinematics

import com.areslib.math.geometry.Rotation2d

/**
 * Class implementation for Swerve Module State.
 *
 * Pure Redux state definition and deterministic reducer transition handler.
 */
data class SwerveModuleState(
    var speedMetersPerSecond: Double = 0.0,
    var angle: Rotation2d = Rotation2d()
)
