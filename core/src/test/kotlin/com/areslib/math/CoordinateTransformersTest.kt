package com.areslib.math

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.areslib.state.Alliance
import com.areslib.state.DriveState
import com.areslib.action.RobotAction
import com.areslib.reducer.DriveReducer

class CoordinateTransformersTest {

    @Test
    fun `rotational pose flipping returns same for blue and inverted for red`() {
        val originalPose = Pose2d(1.0, -1.5, Rotation2d(0.5))

        // Blue alliance should be a no-op
        val blueFlipped = CoordinateTransformers.flipPoseRotational(originalPose, Alliance.BLUE)
        assertEquals(originalPose.x, blueFlipped.x, 0.001)
        assertEquals(originalPose.y, blueFlipped.y, 0.001)
        assertEquals(originalPose.heading.radians, blueFlipped.heading.radians, 0.001)

        // Red alliance should rotate 180 degrees
        val redFlipped = CoordinateTransformers.flipPoseRotational(originalPose, Alliance.RED)
        assertEquals(-1.0, redFlipped.x, 0.001)
        assertEquals(1.5, redFlipped.y, 0.001)
        
        val expectedHeading = InputMath.wrapAngle(0.5 + Math.PI)
        assertEquals(expectedHeading, redFlipped.heading.radians, 0.001)
    }

    @Test
    fun `rotational translation flipping returns same for blue and inverted for red`() {
        val originalTrans = Translation2d(0.8, -2.4)

        val blueFlipped = CoordinateTransformers.flipTranslationRotational(originalTrans, Alliance.BLUE)
        assertEquals(originalTrans.x, blueFlipped.x, 0.001)
        assertEquals(originalTrans.y, blueFlipped.y, 0.001)

        val redFlipped = CoordinateTransformers.flipTranslationRotational(originalTrans, Alliance.RED)
        assertEquals(-0.8, redFlipped.x, 0.001)
        assertEquals(2.4, redFlipped.y, 0.001)
    }

    @Test
    fun `corner rotational pose flipping works for red alliance`() {
        val originalPose = Pose2d(1.0, 1.0, Rotation2d(0.0)) // corner-origin

        val redFlipped = CoordinateTransformers.flipCornerPoseRotational(
            originalPose, 
            Alliance.RED, 
            CoordinateTransformers.FTC_FIELD_SIZE, 
            CoordinateTransformers.FTC_FIELD_SIZE
        )
        // 3.6576 - 1.0 = 2.6576
        assertEquals(2.6576, redFlipped.x, 0.001)
        assertEquals(2.6576, redFlipped.y, 0.001)
        val expectedHeading = InputMath.wrapAngle(0.0 + Math.PI)
        assertEquals(expectedHeading, redFlipped.heading.radians, 0.001)
    }

    @Test
    fun `mirror pose reflectional X mirrors x coordinate and flips heading`() {
        val originalPose = Pose2d(1.0, 2.0, Rotation2d(0.5))

        val redMirrored = CoordinateTransformers.mirrorPoseReflectionalX(
            originalPose, 
            Alliance.RED, 
            CoordinateTransformers.FTC_FIELD_SIZE
        )
        // 3.6576 - 1.0 = 2.6576
        assertEquals(2.6576, redMirrored.x, 0.001)
        assertEquals(2.0, redMirrored.y, 0.001)
        
        val expectedHeading = InputMath.wrapAngle(Math.PI - 0.5)
        assertEquals(expectedHeading, redMirrored.heading.radians, 0.001)
    }

    @Test
    fun `mirror translation reflectional X works correctly`() {
        val originalTrans = Translation2d(1.5, 3.0)

        val redMirrored = CoordinateTransformers.mirrorTranslationReflectionalX(
            originalTrans,
            Alliance.RED,
            CoordinateTransformers.FTC_FIELD_SIZE
        )
        assertEquals(2.1576, redMirrored.x, 0.001) // 3.6576 - 1.5
        assertEquals(3.0, redMirrored.y, 0.001)
    }

    @Test
    fun `DriveReducer handles SetAlliance correctly`() {
        val state = DriveState()
        assertEquals(Alliance.BLUE, state.alliance)

        val updatedState = DriveReducer.reduce(state, RobotAction.SetAlliance(Alliance.RED))
        assertEquals(Alliance.RED, updatedState.alliance)
    }
}
