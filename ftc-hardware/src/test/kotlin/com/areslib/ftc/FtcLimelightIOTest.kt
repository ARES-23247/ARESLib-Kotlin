package com.areslib.ftc

import com.areslib.hardware.ftc.vision.FtcLimelightIO
import com.areslib.hardware.vision.VisionIOInputs
import com.qualcomm.hardware.limelightvision.LLResult
import com.qualcomm.hardware.limelightvision.Limelight3A
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D
import org.firstinspires.ftc.robotcore.external.navigation.Position
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FtcLimelightIOTest {

    class MockLLResult(
        private val valid: Boolean,
        private val botpose: Pose3D?
    ) : LLResult() {
        override fun isValid(): Boolean = valid
        override fun getBotpose(): Pose3D? = botpose
    }

    class MockLimelight3A(
        private val result: LLResult?
    ) : Limelight3A() {
        override fun getLatestResult(): LLResult? = result
    }

    @Test
    fun testCoordinateTransformation() {
        // Mock FTC coordinates:
        // Position: X = 1.0 (right), Y = 2.0 (forward), Z = 0.5
        // Heading (Yaw) = 106 degrees (facing forward-ish, since straight forward on field is 90)
        // Pitch = 25 degrees (camera tilt)
        // Roll = 0 degrees
        val ftcPose = Pose3D(
            Position(DistanceUnit.METER, 1.0, 2.0, 0.5, 0),
            YawPitchRollAngles(AngleUnit.DEGREES, 106.0, 25.0, 0.0, 0)
        )

        val mockResult = MockLLResult(valid = true, botpose = ftcPose)
        val mockLimelight = MockLimelight3A(mockResult)
        val limelightIO = FtcLimelightIO(mockLimelight)

        val inputs = VisionIOInputs()
        limelightIO.updateInputs(inputs)

        assertTrue(inputs.isConnected)
        assertEquals(1, inputs.measurements.size)

        val measurement = inputs.measurements[0]
        val transformedPose3d = measurement.targetPose
        val transformedPose2d = transformedPose3d.toPose2d()

        // Verify Translation Transformation:
        // x_wpi = y_ftc = 2.0
        // y_wpi = -x_ftc = -1.0
        // z_wpi = z_ftc = 0.5
        assertEquals(2.0, transformedPose3d.x, 1e-6)
        assertEquals(-1.0, transformedPose3d.y, 1e-6)
        assertEquals(0.5, transformedPose3d.z, 1e-6)

        assertEquals(2.0, transformedPose2d.x, 1e-6)
        assertEquals(-1.0, transformedPose2d.y, 1e-6)

        // Verify Orientation Transformation:
        // Yaw_wpi should be Yaw_ftc - 90 degrees = 106 - 90 = 16 degrees
        val expectedYawRad = Math.toRadians(16.0)
        assertEquals(expectedYawRad, transformedPose2d.heading.radians, 1e-6)
    }
}
