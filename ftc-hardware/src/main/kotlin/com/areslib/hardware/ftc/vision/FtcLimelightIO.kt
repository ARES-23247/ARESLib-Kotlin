package com.areslib.hardware.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d
import com.qualcomm.hardware.limelightvision.Limelight3A
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit

class FtcLimelightIO(private val limelight: Limelight3A) : VisionIO {
    
    private var lastWarningTime = 0L

    init {
        try {
            limelight.start()
        } catch (e: Throwable) {
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            System.err.println("FtcLimelightIO: Failed to start Limelight during initialization. Error: ${e.message}")
            e.printStackTrace()
            lastWarningTime = now
        }
    }

    override fun updateInputs(inputs: VisionIOInputs) {
        try {
            val result = limelight.getLatestResult()
            
            inputs.isConnected = result != null && result.isValid()
            
            if (result != null && result.isValid()) {
                val botpose = result.getBotpose()
                if (botpose != null) {
                    val pos = botpose.position.toUnit(DistanceUnit.METER)
                    val orient = botpose.orientation
                    
                    // Transform FTC coordinates (+Y forward, +X right) to WPILib coordinates (+X forward, +Y left)
                    val wpiTranslation = com.areslib.math.Translation3d(
                        x = pos.y,
                        y = -pos.x,
                        z = pos.z
                    )
                    
                    // Transform orientation: R_wpi = R(0, 0, -pi/2) * R_ftc
                    val ftcRotation = com.areslib.math.Rotation3d(
                        roll = orient.getRoll(AngleUnit.RADIANS),
                        pitch = orient.getPitch(AngleUnit.RADIANS),
                        yaw = orient.getYaw(AngleUnit.RADIANS)
                    )
                    val frameRotation = com.areslib.math.Rotation3d(0.0, 0.0, -Math.PI / 2.0)
                    val wpiRotation = frameRotation * ftcRotation
                    
                    val pose = Pose3d(
                        translation = wpiTranslation,
                        rotation = wpiRotation
                    )
                    
                    val measurement = VisionMeasurement(
                        timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                        targetPose = pose,
                        tagId = -1, // Limelight result might give primary tag, but we mock -1 for now
                        ambiguity = 0.0 // Could map from result.ta
                    )
                    
                    inputs.measurements = listOf(measurement)
                } else {
                    inputs.measurements = emptyList()
                }
            } else {
                inputs.measurements = emptyList()
            }
        } catch (e: Throwable) {
            inputs.isConnected = false
            inputs.measurements = emptyList()
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            if (now - lastWarningTime > 2000L) {
                System.err.println("FtcLimelightIO: Error reading from Limelight hardware. Error: ${e.message}")
                e.printStackTrace()
                lastWarningTime = now
            }
        }
    }
}

