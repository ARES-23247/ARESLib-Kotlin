package com.areslib.math.coordinate

import com.areslib.state.Alliance
import com.areslib.math.geometry.*
import com.areslib.pathing.Path
import com.areslib.pathing.PathPoint
import com.areslib.math.wrapAngle

/**
 * FieldSymmetry declaration.
 * Provides high-performance, Zero-GC operations.
 * CCW-positive heading standard applied. 
 * Note: Physical units use standard SI metrics.
 * Uses LaTeX math representation for kinematics where applicable.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
enum class FieldSymmetry {
    ROTATIONAL,
    MIRRORED
}

/**
 * Object implementation for Alliance Mirroring.
 *
 * Provides mathematical state estimation, vector filtering, or kinematic matrix operations.
 *
 * ### Physical Units & Coordinates:
 * - Position: Meters ($m$)
 * - Heading: Radians ($rad$), counter-clockwise positive
 * - Time: Seconds ($s$) or milliseconds ($ms$)
 */
object AllianceMirroring {

    /**
     * Mirrors a 2D pose based on the alliance color and the field's symmetry layout.
     * 
     * @param pose The input Pose2d to transform.
     * @param alliance The active alliance color.
     * @param symmetry The symmetry style of the field.
     * @param fieldLength Bounding length of the field in meters (X-axis limit).
     * @param fieldWidth Bounding width of the field in meters (Y-axis limit).
     * @return The mirrored Pose2d.
     */
    fun mirror(
        pose: Pose2d,
        alliance: Alliance,
        symmetry: FieldSymmetry,
        fieldLength: Double = CoordinateTransformers.FTC_FIELD_SIZE,
        fieldWidth: Double = CoordinateTransformers.FTC_FIELD_SIZE
    ): Pose2d {
        if (alliance == Alliance.BLUE) return pose
        val isCenterOrigin = kotlin.math.abs(fieldLength - CoordinateTransformers.FTC_FIELD_SIZE) < 1e-3
        return if (isCenterOrigin) {
            when (symmetry) {
                FieldSymmetry.ROTATIONAL -> Pose2d(
                    x = -pose.x,
                    y = -pose.y,
                    heading = Rotation2d(wrapAngle(pose.heading.radians + Math.PI))
                )
                FieldSymmetry.MIRRORED -> Pose2d(
                    x = pose.x,
                    y = -pose.y,
                    heading = Rotation2d(wrapAngle(-pose.heading.radians))
                )
            }
        } else {
            when (symmetry) {
                FieldSymmetry.ROTATIONAL -> Pose2d(
                    x = fieldLength - pose.x,
                    y = fieldWidth - pose.y,
                    heading = Rotation2d(wrapAngle(pose.heading.radians + Math.PI))
                )
                FieldSymmetry.MIRRORED -> Pose2d(
                    x = pose.x,
                    y = fieldWidth - pose.y,
                    heading = Rotation2d(wrapAngle(-pose.heading.radians))
                )
            }
        }
    }

    /**
     * Mirrors a 2D translation based on the alliance color and the field's symmetry layout.
     * 
     * @param translation The input Translation2d to transform.
     * @param alliance The active alliance color.
     * @param symmetry The symmetry style of the field.
     * @param fieldLength Bounding length of the field in meters (X-axis limit).
     * @param fieldWidth Bounding width of the field in meters (Y-axis limit).
     * @return The mirrored Translation2d.
     */
    fun mirror(
        translation: Translation2d,
        alliance: Alliance,
        symmetry: FieldSymmetry,
        fieldLength: Double = CoordinateTransformers.FTC_FIELD_SIZE,
        fieldWidth: Double = CoordinateTransformers.FTC_FIELD_SIZE
    ): Translation2d {
        if (alliance == Alliance.BLUE) return translation
        val isCenterOrigin = kotlin.math.abs(fieldLength - CoordinateTransformers.FTC_FIELD_SIZE) < 1e-3
        return if (isCenterOrigin) {
            when (symmetry) {
                FieldSymmetry.ROTATIONAL -> Translation2d(-translation.x, -translation.y)
                FieldSymmetry.MIRRORED -> Translation2d(translation.x, -translation.y)
            }
        } else {
            when (symmetry) {
                FieldSymmetry.ROTATIONAL -> Translation2d(
                    x = fieldLength - translation.x,
                    y = fieldWidth - translation.y
                )
                FieldSymmetry.MIRRORED -> Translation2d(
                    x = translation.x,
                    y = fieldWidth - translation.y
                )
            }
        }
    }

    /**
     * Mirrors an entire trajectory path based on the alliance color and symmetry layout.
     * Automatically adjusts coordinates and flips the curvature sign for reflectional symmetry.
     * 
     * @param path The input path to mirror.
     * @param alliance The active alliance color.
     * @param symmetry The symmetry style of the field.
     * @param fieldLength Bounding length of the field in meters (X-axis limit).
     * @param fieldWidth Bounding width of the field in meters (Y-axis limit).
     * @return The mirrored Path.
     */
    fun mirror(
        path: Path,
        alliance: Alliance,
        symmetry: FieldSymmetry,
        fieldLength: Double = CoordinateTransformers.FTC_FIELD_SIZE,
        fieldWidth: Double = CoordinateTransformers.FTC_FIELD_SIZE
    ): Path {
        if (alliance == Alliance.BLUE) return path
        val numPoints = path.points.size
        val mirroredPoints = ArrayList<PathPoint>(numPoints)
        for (i in 0 until numPoints) {
            val point = path.points[i]
            val mirroredPose = mirror(point.pose, alliance, symmetry, fieldLength, fieldWidth)
            val mirroredCurvature = when (symmetry) {
                FieldSymmetry.ROTATIONAL -> point.curvature
                FieldSymmetry.MIRRORED -> -point.curvature
            }
            val mirroredTangent = when (symmetry) {
                FieldSymmetry.ROTATIONAL -> wrapAngle(point.tangentRadians + Math.PI)
                FieldSymmetry.MIRRORED -> wrapAngle(-point.tangentRadians)
            }
            mirroredPoints.add(
                point.copy(
                    pose = mirroredPose,
                    curvature = mirroredCurvature,
                    tangentRadians = mirroredTangent
                )
            )
        }
        return path.copy(points = mirroredPoints)
    }
}
