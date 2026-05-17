package com.areslib.math

object CoordinateTransformers {
    // FTC Standard field is 3.65m x 3.65m (approx 12ft x 12ft)
    // Center is 1.8288, 1.8288
    private const val FIELD_CENTER_X = 1.8288
    private const val FIELD_CENTER_Y = 1.8288

    /**
     * Converts a Center-Origin pose (AdvantageScope/Dyn4j) to a Corner-Origin pose (PathPlanner).
     * @param centerPose Pose with origin at (0,0) in the middle of the field
     * @return Pose mapped to PathPlanner's Bottom-Right corner origin
     */
    fun centerToCorner(centerPose: Pose2d): Pose2d {
        return Pose2d(
            x = centerPose.x + FIELD_CENTER_X,
            y = centerPose.y + FIELD_CENTER_Y,
            heading = centerPose.heading
        )
    }

    /**
     * Converts a Corner-Origin pose (PathPlanner) to a Center-Origin pose (AdvantageScope/Dyn4j).
     * @param cornerPose Pose with origin at (0,0) at the bottom-right corner of the field
     * @return Pose mapped to the center of the field
     */
    fun cornerToCenter(cornerPose: Pose2d): Pose2d {
        return Pose2d(
            x = cornerPose.x - FIELD_CENTER_X,
            y = cornerPose.y - FIELD_CENTER_Y,
            heading = cornerPose.heading
        )
    }
}
