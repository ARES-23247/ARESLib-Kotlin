package com.areslib.math.geometry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeometryTest {

    @Test
    fun testRotation2dWrapping() {
        // 1. Check exact bounds
        assertEquals(0.0, Rotation2d(0.0).radians, 1e-6)
        assertEquals(-Math.PI, Rotation2d(Math.PI).radians, 1e-6)
        assertEquals(-Math.PI, Rotation2d(-Math.PI).radians, 1e-6)

        // 2. Check positive overflow wrapping
        assertEquals(-Math.PI / 2.0, Rotation2d(1.5 * Math.PI).radians, 1e-6)
        assertEquals(0.0, Rotation2d(4.0 * Math.PI).radians, 1e-6)
        assertEquals(Math.toRadians(10.0), Rotation2d.fromDegrees(370.0).radians, 1e-6)

        // 3. Check negative underflow wrapping
        assertEquals(Math.PI / 2.0, Rotation2d(-1.5 * Math.PI).radians, 1e-6)
        assertEquals(-Math.PI / 2.0, Rotation2d(-2.5 * Math.PI).radians, 1e-6)
        assertEquals(Math.toRadians(-10.0), Rotation2d.fromDegrees(-370.0).radians, 1e-6)
    }

    @Test
    fun testRotation2dCosAndSin() {
        val r0 = Rotation2d(0.0)
        assertEquals(1.0, r0.cos, 1e-6)
        assertEquals(0.0, r0.sin, 1e-6)

        val r90 = Rotation2d.fromDegrees(90.0)
        assertEquals(0.0, r90.cos, 1e-6)
        assertEquals(1.0, r90.sin, 1e-6)
    }

    @Test
    fun testTranslation2dNorm() {
        val t = Translation2d(3.0, 4.0)
        assertEquals(5.0, t.norm, 1e-6)
    }

    @Test
    fun testTranslation2dDefaultConstructorAndOriginNorm() {
        val defaultTranslation = Translation2d()
        assertEquals(0.0, defaultTranslation.x, 1e-6)
        assertEquals(0.0, defaultTranslation.y, 1e-6)
        assertEquals(0.0, defaultTranslation.norm, 1e-6)

        val explicitOrigin = Translation2d(0.0, 0.0)
        assertEquals(0.0, explicitOrigin.x, 1e-6)
        assertEquals(0.0, explicitOrigin.y, 1e-6)
        assertEquals(0.0, explicitOrigin.norm, 1e-6)
    }

    @Test
    fun testPose2dDefaultValues() {
        val defaultPose = Pose2d()
        assertEquals(0.0, defaultPose.x, 1e-6)
        assertEquals(0.0, defaultPose.y, 1e-6)
        assertEquals(0.0, defaultPose.heading.radians, 1e-6)
        assertEquals(Rotation2d(0.0), defaultPose.heading)
        assertEquals(0.0, defaultPose.translation.x, 1e-6)
        assertEquals(0.0, defaultPose.translation.y, 1e-6)
        assertEquals(0.0, defaultPose.translation.norm, 1e-6)
        assertEquals(Translation2d(0.0, 0.0), defaultPose.translation)
    }

    @Test
    fun testPose2dTranslationExtractionAndFormatting() {
        val pose = Pose2d(1.5, -2.5, Rotation2d.fromDegrees(45.0))
        assertEquals(1.5, pose.translation.x, 1e-6)
        assertEquals(-2.5, pose.translation.y, 1e-6)
        assertEquals("(1.50, -2.50) 45.0°", pose.toFormattedString())
    }

    @Test
    fun testRotation2dFromDegreesWrappingBoundaries() {
        // +/-180.0 degrees
        assertEquals(-Math.PI, Rotation2d.fromDegrees(180.0).radians, 1e-6)
        assertEquals(-Math.PI, Rotation2d.fromDegrees(-180.0).radians, 1e-6)

        // +/-360.0 degrees
        assertEquals(0.0, Rotation2d.fromDegrees(360.0).radians, 1e-6)
        assertEquals(0.0, Rotation2d.fromDegrees(-360.0).radians, 1e-6)

        // +/-540.0 degrees
        assertEquals(-Math.PI, Rotation2d.fromDegrees(540.0).radians, 1e-6)
        assertEquals(-Math.PI, Rotation2d.fromDegrees(-540.0).radians, 1e-6)

        // +/-720.0 degrees
        assertEquals(0.0, Rotation2d.fromDegrees(720.0).radians, 1e-6)
        assertEquals(0.0, Rotation2d.fromDegrees(-720.0).radians, 1e-6)
    }
}
