package com.areslib.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

data class Translation2d(var x: Double = 0.0, var y: Double = 0.0) {
    val norm: Double get() = hypot(x, y)
}

data class Rotation2d(val rawRadians: Double = 0.0) {
    val radians: Double get() = InputMath.wrapAngle(rawRadians)
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

/**
 * Formats a Pose2d into a standard human-readable format: "(X.XX, Y.YY) DEGREES°".
 */
fun Pose2d.toFormattedString(): String {
    return String.format("(%.2f, %.2f) %.1f°", x, y, Math.toDegrees(heading.radians))
}
