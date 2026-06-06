package com.areslib.math

import com.areslib.state.Alliance

object CoordinateTransformers {
    const val FTC_FIELD_SIZE = 3.6576
    const val FRC_FIELD_LENGTH = 16.54175
    const val FRC_FIELD_WIDTH = 8.21055

    /**
     * Converts a Center-Origin pose (AdvantageScope/Dyn4j) to a Corner-Origin pose (PathPlanner).
     * @param centerPose Pose with origin at (0,0) in the middle of the field
     * @param fieldLength Length of the field in meters (default is FTC size)
     * @param fieldWidth Width of the field in meters (default is FTC size)
     * @return Pose mapped to PathPlanner's Bottom-Right corner origin
     */
    @JvmOverloads
    @JvmStatic
    fun centerToCorner(
        centerPose: Pose2d,
        fieldLength: Double = FTC_FIELD_SIZE,
        fieldWidth: Double = FTC_FIELD_SIZE
    ): Pose2d {
        return Pose2d(
            x = centerPose.x + (fieldLength / 2.0),
            y = centerPose.y + (fieldWidth / 2.0),
            heading = centerPose.heading
        )
    }

    /**
     * Converts a Corner-Origin pose (PathPlanner) to a Center-Origin pose (AdvantageScope/Dyn4j).
     * @param cornerPose Pose with origin at (0,0) at the bottom-right corner of the field
     * @param fieldLength Length of the field in meters (default is FTC size)
     * @param fieldWidth Width of the field in meters (default is FTC size)
     * @return Pose mapped to the center of the field
     */
    @JvmOverloads
    @JvmStatic
    fun cornerToCenter(
        cornerPose: Pose2d,
        fieldLength: Double = FTC_FIELD_SIZE,
        fieldWidth: Double = FTC_FIELD_SIZE
    ): Pose2d {
        return Pose2d(
            x = cornerPose.x - (fieldLength / 2.0),
            y = cornerPose.y - (fieldWidth / 2.0),
            heading = cornerPose.heading
        )
    }

    /**
     * Flips a pose relative to the field center (180 degrees rotation) for the Red Alliance.
     * This represents standard rotational symmetry where the opponent's side is rotated 180 deg.
     */
    fun flipPoseRotational(pose: Pose2d, alliance: Alliance): Pose2d {
        if (alliance == Alliance.BLUE) return pose
        return Pose2d(
            x = -pose.x,
            y = -pose.y,
            heading = Rotation2d(InputMath.wrapAngle(pose.heading.radians + Math.PI))
        )
    }

    /**
     * Flips a translation relative to the field center (180 degrees rotation) for the Red Alliance.
     */
    fun flipTranslationRotational(translation: Translation2d, alliance: Alliance): Translation2d {
        if (alliance == Alliance.BLUE) return translation
        return Translation2d(-translation.x, -translation.y)
    }

    /**
     * Flips an absolute corner-origin pose (e.g. from PathPlanner) using rotational symmetry (180 degrees rotation)
     * about the center of a field of given size.
     */
    fun flipCornerPoseRotational(pose: Pose2d, alliance: Alliance, fieldLength: Double = FTC_FIELD_SIZE, fieldWidth: Double = FTC_FIELD_SIZE): Pose2d {
        if (alliance == Alliance.BLUE) return pose
        return Pose2d(
            x = fieldLength - pose.x,
            y = fieldWidth - pose.y,
            heading = Rotation2d(InputMath.wrapAngle(pose.heading.radians + Math.PI))
        )
    }

    /**
     * Flips an absolute corner-origin pose (e.g. from PathPlanner) using reflectional mirroring
     * across the center line perpendicular to the X-axis (standard PathPlanner symmetry) if alliance is RED.
     */
    fun mirrorPoseReflectionalX(pose: Pose2d, alliance: Alliance, fieldLength: Double = FTC_FIELD_SIZE): Pose2d {
        if (alliance == Alliance.BLUE) return pose
        return Pose2d(
            x = fieldLength - pose.x,
            y = pose.y,
            heading = Rotation2d(InputMath.wrapAngle(Math.PI - pose.heading.radians))
        )
    }

    /**
     * Flips an absolute corner-origin translation using reflectional mirroring
     * across the center line perpendicular to the X-axis if alliance is RED.
     */
    fun mirrorTranslationReflectionalX(translation: Translation2d, alliance: Alliance, fieldLength: Double = FTC_FIELD_SIZE): Translation2d {
        if (alliance == Alliance.BLUE) return translation
        return Translation2d(fieldLength - translation.x, translation.y)
    }
}
