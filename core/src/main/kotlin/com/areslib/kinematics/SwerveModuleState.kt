package com.areslib.kinematics

import com.areslib.math.geometry.Rotation2d

data class SwerveModuleState(
    val speedMetersPerSecond: Double = 0.0,
    val angle: Rotation2d = Rotation2d()
)
