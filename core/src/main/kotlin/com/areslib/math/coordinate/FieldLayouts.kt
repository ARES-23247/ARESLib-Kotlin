package com.areslib.math.coordinate

import com.areslib.math.geometry.*

/**
 * Represents the physical layout of the FTC field.
 */
enum class FieldLayout {
    /** Standard Square layout: Red Alliance Wall on the right when viewed from audience */
    SQUARE_STANDARD,
    /** Diamond layout (e.g., RES-Q): Field rotated 45 degrees, Alliance walls adjacent */
    DIAMOND
}

/**
 * Pre-defined AprilTag coordinate maps for the EKF frame of reference based on the field layout.
 */
object FieldLayouts {
    
    /** Standard Square field tag coordinates mapped to EKF/WPILib frame */
    val SQUARE_STANDARD_TAGS = mapOf(
        // Blue tags on +Y wall, facing -Y (-90 degrees)
        1 to Pose3d(Translation3d(1.8, 1.8, 0.5), Rotation3d(0.0, 0.0, -Math.PI / 2)),
        2 to Pose3d(Translation3d(-1.8, 1.8, 0.5), Rotation3d(0.0, 0.0, -Math.PI / 2)),
        // Red tags on -Y wall, facing +Y (+90 degrees)
        3 to Pose3d(Translation3d(1.8, -1.8, 0.5), Rotation3d(0.0, 0.0, Math.PI / 2)),
        4 to Pose3d(Translation3d(-1.8, -1.8, 0.5), Rotation3d(0.0, 0.0, Math.PI / 2))
    )

    /** Diamond field tag coordinates mapped to EKF/WPILib frame */
    val DIAMOND_TAGS = mapOf(
        1 to Pose3d(Translation3d(1.8, 1.8, 0.5), Rotation3d(0.0, 0.0, Math.PI)),
        2 to Pose3d(Translation3d(1.8, -1.8, 0.5), Rotation3d(0.0, 0.0, Math.PI)),
        3 to Pose3d(Translation3d(-1.8, 1.8, 0.5), Rotation3d(0.0, 0.0, 0.0)),
        4 to Pose3d(Translation3d(-1.8, -1.8, 0.5), Rotation3d(0.0, 0.0, 0.0))
    )

    /**
     * Helper to retrieve the default AprilTag coordinates map for a given field layout.
     */
    fun getTagsForLayout(layout: FieldLayout): Map<Int, Pose3d> {
        return when (layout) {
            FieldLayout.SQUARE_STANDARD -> SQUARE_STANDARD_TAGS
            FieldLayout.DIAMOND -> DIAMOND_TAGS
        }
    }
}
