package com.areslib.math.geometry

/**
 * Mutable 3x3 matrix scratchpad optimized for zero-GC overhead in EKF hot paths.
 * Used primarily for 3-DOF Pose2d covariance in Kalman Filters.
 *
 * Fields are intentionally `var` with in-place mutators ([setTo], [addInPlace], [multiplyInPlace])
 * to enable pre-allocated scratchpad reuse without heap allocations during control loop execution.
 * Immutable operations ([plus], [minus], [times]) return new instances and should be used
 * only outside hot paths (e.g., initialization, configuration).
 */
data class Matrix3x3(
    var m00: Double = 0.0, var m01: Double = 0.0, var m02: Double = 0.0,
    var m10: Double = 0.0, var m11: Double = 0.0, var m12: Double = 0.0,
    var m20: Double = 0.0, var m21: Double = 0.0, var m22: Double = 0.0
) {
    operator fun plus(other: Matrix3x3) = Matrix3x3(
        m00 + other.m00, m01 + other.m01, m02 + other.m02,
        m10 + other.m10, m11 + other.m11, m12 + other.m12,
        m20 + other.m20, m21 + other.m21, m22 + other.m22
    )

    fun setTo(other: Matrix3x3) {
        m00 = other.m00; m01 = other.m01; m02 = other.m02
        m10 = other.m10; m11 = other.m11; m12 = other.m12
        m20 = other.m20; m21 = other.m21; m22 = other.m22
    }

    fun addInPlace(other: Matrix3x3) {
        m00 += other.m00; m01 += other.m01; m02 += other.m02
        m10 += other.m10; m11 += other.m11; m12 += other.m12
        m20 += other.m20; m21 += other.m21; m22 += other.m22
    }

    fun multiplyInPlace(scalar: Double) {
        m00 *= scalar; m01 *= scalar; m02 *= scalar
        m10 *= scalar; m11 *= scalar; m12 *= scalar
        m20 *= scalar; m21 *= scalar; m22 *= scalar
    }


    operator fun minus(other: Matrix3x3) = Matrix3x3(
        m00 - other.m00, m01 - other.m01, m02 - other.m02,
        m10 - other.m10, m11 - other.m11, m12 - other.m12,
        m20 - other.m20, m21 - other.m21, m22 - other.m22
    )

    operator fun times(scalar: Double) = Matrix3x3(
        m00 * scalar, m01 * scalar, m02 * scalar,
        m10 * scalar, m11 * scalar, m12 * scalar,
        m20 * scalar, m21 * scalar, m22 * scalar
    )

    operator fun times(other: Matrix3x3): Matrix3x3 {
        return Matrix3x3(
            m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
            m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
            m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
            
            m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
            m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
            m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
            
            m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
            m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
            m20 * other.m02 + m21 * other.m12 + m22 * other.m22
        )
    }
    
    // Matrix * Vector3
    operator fun times(vector: Vector3): Vector3 {
        return Vector3(
            m00 * vector.x + m01 * vector.y + m02 * vector.z,
            m10 * vector.x + m11 * vector.y + m12 * vector.z,
            m20 * vector.x + m21 * vector.y + m22 * vector.z
        )
    }

    fun transpose() = Matrix3x3(
        m00, m10, m20,
        m01, m11, m21,
        m02, m12, m22
    )

    fun inverse(): Matrix3x3 {
        val det = m00 * (m11 * m22 - m12 * m21) -
                  m01 * (m10 * m22 - m12 * m20) +
                  m02 * (m10 * m21 - m11 * m20)

        if (det.isNaN() || det.isInfinite() || kotlin.math.abs(det) < 1e-9) return Matrix3x3() // Return zero matrix if non-invertible

        val invDet = 1.0 / det

        return Matrix3x3(
             (m11 * m22 - m12 * m21) * invDet,
            -(m01 * m22 - m02 * m21) * invDet,
             (m01 * m12 - m02 * m11) * invDet,
            
            -(m10 * m22 - m12 * m20) * invDet,
             (m00 * m22 - m02 * m20) * invDet,
            -(m00 * m12 - m02 * m10) * invDet,
            
             (m10 * m21 - m11 * m20) * invDet,
            -(m00 * m21 - m01 * m20) * invDet,
             (m00 * m11 - m01 * m10) * invDet
        )
    }

    companion object {
        val IDENTITY get() = Matrix3x3(
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )
    }
}

data class Vector3(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3(x * scalar, y * scalar, z * scalar)
    
    // Outer product yielding 3x3 matrix
    fun outerProduct(other: Vector3): Matrix3x3 {
        return Matrix3x3(
            x * other.x, x * other.y, x * other.z,
            y * other.x, y * other.y, y * other.z,
            z * other.x, z * other.y, z * other.z
        )
    }
}
