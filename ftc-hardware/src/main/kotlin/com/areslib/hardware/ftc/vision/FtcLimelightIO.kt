package com.areslib.hardware.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d
import com.areslib.math.Translation3d
import com.areslib.math.Rotation3d
import com.qualcomm.hardware.limelightvision.Limelight3A
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit

class FtcLimelightIO(
    private val limelight: Limelight3A,
    override val cameraPoses: List<Pose3d> = listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)))
) : VisionIO {
    
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
        inputs.cameraPoses = cameraPoses
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
                    
                    val fiducials = result.getFiducialResults()
                    if (fiducials?.isNotEmpty() == true) {
                        val measurements = ArrayList<VisionMeasurement>(fiducials.size)
                        for (i in 0 until fiducials.size) {
                            val f = fiducials[i]
                            val robotPoseTargetSpaceFTC = f.getRobotPoseTargetSpace()
                            val posTargetMeters = robotPoseTargetSpaceFTC.position.toUnit(DistanceUnit.METER)
                            val orientTarget = robotPoseTargetSpaceFTC.orientation
                            
                            val robotPoseTargetSpaceWpi = Pose3d(
                                translation = com.areslib.math.Translation3d(
                                    x = posTargetMeters.x,
                                    y = posTargetMeters.y,
                                    z = posTargetMeters.z
                                ),
                                rotation = com.areslib.math.Rotation3d(
                                    roll = orientTarget.getRoll(AngleUnit.RADIANS),
                                    pitch = orientTarget.getPitch(AngleUnit.RADIANS),
                                    yaw = orientTarget.getYaw(AngleUnit.RADIANS)
                                )
                            )
                            
                            val measurement = VisionMeasurement(
                                timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                                targetPose = pose,
                                tagId = f.getFiducialId(),
                                ambiguity = 0.0,
                                robotPoseTargetSpace = robotPoseTargetSpaceWpi
                            )
                            measurements.add(measurement)
                        }
                        inputs.measurements = measurements
                    } else {
                        val measurement = VisionMeasurement(
                            timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                            targetPose = pose,
                            tagId = -1,
                            ambiguity = 0.0
                        )
                        inputs.measurements = listOf(measurement)
                    }
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

