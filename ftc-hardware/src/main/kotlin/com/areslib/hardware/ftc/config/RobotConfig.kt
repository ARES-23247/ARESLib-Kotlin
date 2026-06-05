package com.areslib.hardware.ftc.config

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.hardware.limelightvision.Limelight3A
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor
import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.CompositeVisionIO
import com.areslib.hardware.ftc.vision.FtcLimelightIO
import com.areslib.hardware.ftc.vision.FtcVisionPortalIO

/**
 * Central hardware dependency injector for FTC.
 * This class abstracts the `hardwareMap.get` logic away from OpModes.
 */
class RobotConfig(private val hardwareMap: HardwareMap) {

    /**
     * Initializes a Limelight3A vision wrapper.
     */
    fun getLimelight(deviceName: String = "limelight"): VisionIO {
        val names = deviceName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (names.size > 1) {
            val ios = names.map { name ->
                val ll = hardwareMap.get(Limelight3A::class.java, name)
                FtcLimelightIO(ll)
            }
            CompositeVisionIO(ios)
        } else {
            val name = names.firstOrNull() ?: "limelight"
            val ll = hardwareMap.get(Limelight3A::class.java, name)
            FtcLimelightIO(ll)
        }
    }
    
    /**
     * Initializes a VisionPortal AprilTag processor wrapper.
     * Note: The processor must already be active/built by the VisionPortal builder.
     */
    fun getAprilTagVision(processor: AprilTagProcessor): VisionIO {
        return FtcVisionPortalIO(processor)
    }
    
    // Future expansion: getDriveMotors(), getImu(), etc.
}
