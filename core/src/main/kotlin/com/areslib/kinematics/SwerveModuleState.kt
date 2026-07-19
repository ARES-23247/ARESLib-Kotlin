package com.areslib.kinematics

import com.areslib.math.geometry.Rotation2d

data class SwerveModuleState(
    var speedMetersPerSecond: Double = 0.0,
    var angle: Rotation2d = Rotation2d()
)
