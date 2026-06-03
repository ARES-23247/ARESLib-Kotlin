package com.areslib.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

data class Translation2d(val x: Double = 0.0, val y: Double = 0.0) {
    val norm: Double get() = hypot(x, y)
}

data class Rotation2d(val rawRadians: Double = 0.0) {
    val radians: Double = InputMath.wrapAngle(rawRadians)
    val cos: Double get() = cos(radians)
    val sin: Double get() = sin(radians)
    
    companion object {
        fun fromDegrees(degrees: Double): Rotation2d = Rotation2d(Math.toRadians(degrees))
    }
}

data class Pose2d(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val heading: Rotation2d = Rotation2d()
) {
    val translation: Translation2d get() = Translation2d(x, y)
}
