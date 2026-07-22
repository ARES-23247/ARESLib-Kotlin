package com.areslib.math.geometry

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import com.areslib.math.wrapAngle

/**
 * Class implementation for Translation2d.
 *
 * Provides mathematical state estimation, vector filtering, or kinematic matrix operations.
 *
 * ### Physical Units & Coordinates:
 * - Position: Meters ($m$)
 * - Heading: Radians ($rad$), counter-clockwise positive
 * - Time: Seconds ($s$) or milliseconds ($ms$)
 */
data class Translation2d(val x: Double = 0.0, val y: Double = 0.0) {
    val norm: Double get() = hypot(x, y)
}

@JvmInline
value class Rotation2d(val rawRadians: Double = 0.0) {
    val radians: Double get() = wrapAngle(rawRadians)
    val cos: Double get() = cos(radians)
    val sin: Double get() = sin(radians)
    
    companion object {
        fun fromDegrees(degrees: Double): Rotation2d = Rotation2d(Math.toRadians(degrees))
    }
}

/**
 * Class implementation for Pose2d.
 *
 * Provides mathematical state estimation, vector filtering, or kinematic matrix operations.
 *
 * ### Physical Units & Coordinates:
 * - Position: Meters ($m$)
 * - Heading: Radians ($rad$), counter-clockwise positive
 * - Time: Seconds ($s$) or milliseconds ($ms$)
 */
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
fun Pose2d.toFormattedString(): String =
    String.format("(%.2f, %.2f) %.1f°", x, y, Math.toDegrees(heading.radians))
