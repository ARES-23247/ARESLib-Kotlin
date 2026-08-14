package com.areslib.pathing

import com.areslib.math.geometry.Translation2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class BezierSplineTest {

    @Test
    fun testPositionEvaluation() {
        val p0 = Translation2d(0.0, 0.0)
        val p1 = Translation2d(1.0, 0.0)
        val p2 = Translation2d(1.0, 1.0)
        val p3 = Translation2d(2.0, 1.0)

        // t = 0 should be p0
        val pos0 = BezierSpline.evaluate(p0, p1, p2, p3, 0.0)
        assertEquals(p0.x, pos0.x, 0.001)
        assertEquals(p0.y, pos0.y, 0.001)

        // t = 1 should be p3
        val pos1 = BezierSpline.evaluate(p0, p1, p2, p3, 1.0)
        assertEquals(p3.x, pos1.x, 0.001)
        assertEquals(p3.y, pos1.y, 0.001)

        // t = 0.5 should be midpoint logic for this symmetric control polygon
        val posHalf = BezierSpline.evaluate(p0, p1, p2, p3, 0.5)
        assertEquals(1.0, posHalf.x, 0.001)
        assertEquals(0.5, posHalf.y, 0.001)
    }

    @Test
    fun testDerivativeContinuity() {
        val p0 = Translation2d(0.0, 0.0)
        val p1 = Translation2d(1.0, 0.0)
        val p2 = Translation2d(1.0, 1.0)
        val p3 = Translation2d(2.0, 1.0)

        val d1 = BezierSpline.evaluateDerivative(p0, p1, p2, p3, 0.499)
        val d2 = BezierSpline.evaluateDerivative(p0, p1, p2, p3, 0.500)
        val d3 = BezierSpline.evaluateDerivative(p0, p1, p2, p3, 0.501)

        // Derivative should be continuous, small step -> small change
        assertTrue(abs(d2.x - d1.x) < 0.1)
        assertTrue(abs(d2.y - d1.y) < 0.1)
        assertTrue(abs(d3.x - d2.x) < 0.1)
        assertTrue(abs(d3.y - d2.y) < 0.1)
    }

    @Test
    fun testStraightLineDegenerateCase() {
        val p0 = Translation2d(0.0, 0.0)
        val p1 = Translation2d(1.0, 1.0)
        val p2 = Translation2d(2.0, 2.0)
        val p3 = Translation2d(3.0, 3.0)

        val posHalf = BezierSpline.evaluate(p0, p1, p2, p3, 0.5)
        assertEquals(1.5, posHalf.x, 0.001)
        assertEquals(1.5, posHalf.y, 0.001)
        
        val heading = BezierSpline.evaluateHeading(p0, p1, p2, p3, 0.5)
        assertEquals(Math.PI / 4, heading.radians, 0.001) // 45 degrees
    }

    @Test
    fun testEndpointDerivativesAndCurvature() {
        val p0 = Translation2d(0.0, 0.0)
        val p1 = Translation2d(1.0, 2.0)
        val p2 = Translation2d(3.0, 2.0)
        val p3 = Translation2d(4.0, 0.0)

        // Verify exact derivative vector at t = 0 is 3 * (p1 - p0)
        val d0 = BezierSpline.evaluateDerivative(p0, p1, p2, p3, 0.0)
        val expectedD0X = 3.0 * (p1.x - p0.x)
        val expectedD0Y = 3.0 * (p1.y - p0.y)
        assertEquals(expectedD0X, d0.x, 1e-9)
        assertEquals(expectedD0Y, d0.y, 1e-9)

        // Verify exact derivative vector at t = 1 is 3 * (p3 - p2)
        val d1 = BezierSpline.evaluateDerivative(p0, p1, p2, p3, 1.0)
        val expectedD1X = 3.0 * (p3.x - p2.x)
        val expectedD1Y = 3.0 * (p3.y - p2.y)
        assertEquals(expectedD1X, d1.x, 1e-9)
        assertEquals(expectedD1Y, d1.y, 1e-9)

        // Curvature evaluation at midpoint (t = 0.5):
        // Analytical first derivative at t = 0.5: B'(0.5)
        val dMid = BezierSpline.evaluateDerivative(p0, p1, p2, p3, 0.5)

        // Analytical second derivative at t = 0.5:
        // B''(t) = 6(1-t)(p2 - 2p1 + p0) + 6t(p3 - 2p2 + p1)
        val tMid = 0.5
        val uMid = 1.0 - tMid
        val d2xMid = 6.0 * uMid * (p2.x - 2.0 * p1.x + p0.x) + 6.0 * tMid * (p3.x - 2.0 * p2.x + p1.x)
        val d2yMid = 6.0 * uMid * (p2.y - 2.0 * p1.y + p0.y) + 6.0 * tMid * (p3.y - 2.0 * p2.y + p1.y)

        // Parametric curvature formula: kappa = (x' y'' - y' x'') / (x'^2 + y'^2)^(3/2)
        val speedSqMid = dMid.x * dMid.x + dMid.y * dMid.y
        val curvatureAnalytical = (dMid.x * d2yMid - dMid.y * d2xMid) / Math.pow(speedSqMid, 1.5)

        // For p0=(0,0), p1=(1,2), p2=(3,2), p3=(4,0):
        // dMid = (4.5, 0.0), speedSq = 20.25, d2Mid = (0.0, -12.0)
        // kappa = (4.5 * -12.0 - 0.0) / (4.5)^3 = -54.0 / 91.125 = -16 / 27
        val expectedCurvature = -16.0 / 27.0
        assertEquals(expectedCurvature, curvatureAnalytical, 1e-9)

        // Verify consistency with numerical finite-difference curvature dTheta / ds
        val dt = 1e-5
        val posBefore = BezierSpline.evaluate(p0, p1, p2, p3, tMid - dt)
        val posAfter = BezierSpline.evaluate(p0, p1, p2, p3, tMid + dt)
        val ds = Math.hypot(posAfter.x - posBefore.x, posAfter.y - posBefore.y)

        val headingBefore = BezierSpline.evaluateHeading(p0, p1, p2, p3, tMid - dt)
        val headingAfter = BezierSpline.evaluateHeading(p0, p1, p2, p3, tMid + dt)
        val dTheta = headingAfter.radians - headingBefore.radians
        val curvatureNumerical = dTheta / ds

        assertEquals(curvatureAnalytical, curvatureNumerical, 1e-4)
    }
}
