package com.areslib.math.geometry

import kotlin.math.*

data class Translation3d(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    val norm: Double get() = sqrt(x * x + y * y + z * z)
    operator fun plus(other: Translation3d) = Translation3d(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Translation3d) = Translation3d(x - other.x, y - other.y, z - other.z)
}

data class Quaternion(val w: Double = 1.0, val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    fun normalize(): Quaternion {
        val norm = sqrt(w * w + x * x + y * y + z * z)
        if (norm.isNaN() || norm.isInfinite() || norm == 0.0) return Quaternion()
        return Quaternion(w / norm, x / norm, y / norm, z / norm)
    }

    operator fun times(other: Quaternion): Quaternion {
        return Quaternion(
            w * other.w - x * other.x - y * other.y - z * other.z,
            w * other.x + x * other.w + y * other.z - z * other.y,
            w * other.y - x * other.z + y * other.w + z * other.x,
            w * other.z + x * other.y - y * other.x + z * other.w
        )
    }

    fun inverse(): Quaternion {
        // Assuming unit quaternion for spatial rotation
        return Quaternion(w, -x, -y, -z)
    }
}

data class Rotation3d(val q: Quaternion = Quaternion()) {
    constructor(roll: Double, pitch: Double, yaw: Double) : this(
        fromEulerAngles(roll, pitch, yaw)
    )

    operator fun times(other: Rotation3d): Rotation3d {
        return Rotation3d((q * other.q).normalize())
    }
    
    fun inverse(): Rotation3d {
        return Rotation3d(q.inverse())
    }

    val x: Double get() {
        val sinr_cosp = 2.0 * (q.w * q.x + q.y * q.z)
        val cosr_cosp = 1.0 - 2.0 * (q.x * q.x + q.y * q.y)
        return atan2(sinr_cosp, cosr_cosp)
    }

    val y: Double get() {
        val sinp = 2.0 * (q.w * q.y - q.z * q.x)
        return if (abs(sinp) >= 1.0) {
            (PI / 2).withSign(sinp)
        } else {
            asin(sinp)
        }
    }

    val z: Double get() {
        val siny_cosp = 2.0 * (q.w * q.z + q.x * q.y)
        val cosy_cosp = 1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        return atan2(siny_cosp, cosy_cosp)
    }

    companion object {
        private fun fromEulerAngles(roll: Double, pitch: Double, yaw: Double): Quaternion {
            val cr = cos(roll * 0.5)
            val sr = sin(roll * 0.5)
            val cp = cos(pitch * 0.5)
            val sp = sin(pitch * 0.5)
            val cy = cos(yaw * 0.5)
            val sy = sin(yaw * 0.5)

            val w = cr * cp * cy + sr * sp * sy
            val x = sr * cp * cy - cr * sp * sy
            val y = cr * sp * cy + sr * cp * sy
            val z = cr * cp * sy - sr * sp * cy

            return Quaternion(w, x, y, z)
        }
    }
}

data class Pose3d(
    val translation: Translation3d = Translation3d(),
    val rotation: Rotation3d = Rotation3d()
) {
    val x: Double get() = translation.x
    val y: Double get() = translation.y
    val z: Double get() = translation.z
    
    fun toPose2d(): Pose2d {
        return Pose2d(x, y, Rotation2d(rotation.z))
    }
}

data class Transform3d(
    val translation: Translation3d = Translation3d(),
    val rotation: Rotation3d = Rotation3d()
) {
    fun inverse(): Transform3d {
        val invRot = rotation.inverse()
        val p = Quaternion(0.0, translation.x, translation.y, translation.z)
        val invP = invRot.q * p * invRot.q.inverse()
        return Transform3d(Translation3d(-invP.x, -invP.y, -invP.z), invRot)
    }
}

fun Pose3d.transformBy(other: Transform3d): Pose3d {
    val p = Quaternion(0.0, other.translation.x, other.translation.y, other.translation.z)
    val rotatedTrans = rotation.q * p * rotation.q.inverse()
    val newTrans = Translation3d(
        translation.x + rotatedTrans.x,
        translation.y + rotatedTrans.y,
        translation.z + rotatedTrans.z
    )
    return Pose3d(newTrans, rotation * other.rotation)
}

fun Pose3d.relativeTo(other: Pose3d): Transform3d {
    val invRot = other.rotation.inverse()
    val transDiff = Translation3d(
        translation.x - other.translation.x,
        translation.y - other.translation.y,
        translation.z - other.translation.z
    )
    val p = Quaternion(0.0, transDiff.x, transDiff.y, transDiff.z)
    val rotatedTrans = invRot.q * p * invRot.q.inverse()
    
    return Transform3d(
        Translation3d(rotatedTrans.x, rotatedTrans.y, rotatedTrans.z),
        invRot * rotation
    )
}
