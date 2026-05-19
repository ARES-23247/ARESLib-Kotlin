package org.firstinspires.ftc.robotcore.external.navigation

enum class AngleUnit { DEGREES, RADIANS }
enum class AxesOrder { ZYX, XYZ, YZX, XZY, YXZ, ZXY }
enum class AxesReference { EXTRINSIC, INTRINSIC }
enum class Distance {
    METER, CM, MM, INCH
}

class Orientation(val firstAngle: Float = 0f, val secondAngle: Float = 0f, val thirdAngle: Float = 0f)

enum class CurrentUnit { AMPS, MILLIAMPS }

class YawPitchRollAngles(
    val yawUnit: AngleUnit = AngleUnit.DEGREES,
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val acquisitionTime: Long = 0
) {
    fun getYaw(unit: AngleUnit): Double = if (unit == yawUnit) yaw else Math.toRadians(yaw)
    fun getPitch(unit: AngleUnit): Double = if (unit == yawUnit) pitch else Math.toRadians(pitch)
    fun getRoll(unit: AngleUnit): Double = if (unit == yawUnit) roll else Math.toRadians(roll)
}

class AngularVelocity(
    val unit: AngleUnit = AngleUnit.DEGREES,
    val xRotationRate: Float = 0f,
    val yRotationRate: Float = 0f,
    val zRotationRate: Float = 0f,
    val acquisitionTime: Long = 0
) {
    fun getXRotationRate(unit: AngleUnit): Float = if (unit == this.unit) xRotationRate else Math.toRadians(xRotationRate.toDouble()).toFloat()
    fun getYRotationRate(unit: AngleUnit): Float = if (unit == this.unit) yRotationRate else Math.toRadians(yRotationRate.toDouble()).toFloat()
    fun getZRotationRate(unit: AngleUnit): Float = if (unit == this.unit) zRotationRate else Math.toRadians(zRotationRate.toDouble()).toFloat()
}
