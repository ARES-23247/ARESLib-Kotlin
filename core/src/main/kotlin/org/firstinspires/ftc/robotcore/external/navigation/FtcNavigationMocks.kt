package org.firstinspires.ftc.robotcore.external.navigation

enum class AngleUnit { DEGREES, RADIANS }
enum class AxesOrder { ZYX, XYZ, YZX, XZY, YXZ, ZXY }
enum class AxesReference { EXTRINSIC, INTRINSIC }
enum class Distance {
    METER, CM, MM, INCH
}

class Orientation(val firstAngle: Float = 0f, val secondAngle: Float = 0f, val thirdAngle: Float = 0f)
