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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class FtcLimelightIO(
    private val limelight: Limelight3A,
    override val cameraPoses: List<Pose3d> = listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)))
) : VisionIO, AutoCloseable {
    
    private var lastWarningTime = 0L
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val frameRotation = com.areslib.math.Rotation3d(0.0, 0.0, -Math.PI / 2.0)
    private val scratchMeasurements = ArrayList<VisionMeasurement>(10)

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
                    val wpiRotation = frameRotation * ftcRotation
                    
                    val pose = Pose3d(
                        translation = wpiTranslation,
                        rotation = wpiRotation
                    )
                    
                    val fiducials = result.getFiducialResults()
                    if (fiducials?.isNotEmpty() == true) {
                        scratchMeasurements.clear()
                        for (i in 0 until fiducials.size) {
                            val f = fiducials[i]
                            val robotPoseTargetSpaceFTC = f.getRobotPoseTargetSpace()
                            val posTargetMeters = robotPoseTargetSpaceFTC.position.toUnit(DistanceUnit.METER)
                            val orientTarget = robotPoseTargetSpaceFTC.orientation
                            
                            // IMPORTANT: Unlike botpose (lines 44-58), target-space rotation is
                            // NOT coordinate-transformed from FTC→WPI. The Limelight SDK's
                            // roll/pitch/yaw are passed directly to Rotation3d(roll, pitch, yaw).
                            //
                            // In target-space (Y-up, Z-forward), the robot's HEADING rotation
                            // (left/right turning) is around the Y axis. The Limelight SDK reports
                            // this as getPitch() (not getYaw()), which maps to Rotation3d.y.
                            //
                            // Consumers should use: -rotation.y for heading, NOT rotation.z.
                            // See VisionMeasurement KDoc for the full axis mapping table.
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
                            
                            val distance = kotlin.math.hypot(posTargetMeters.x, posTargetMeters.z)
                            val estAmbiguity = if (distance > 0.0) (0.02 * (distance * distance)).coerceAtMost(0.99) else 0.0

                            val measurement = VisionMeasurement(
                                timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                                targetPose = pose,
                                tagId = f.getFiducialId(),
                                ambiguity = estAmbiguity,
                                robotPoseTargetSpace = robotPoseTargetSpaceWpi
                            )
                            scratchMeasurements.add(measurement)
                        }
                        // Copy to avoid mutating the list in the Redux state
                        inputs.measurements = scratchMeasurements.toList()
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
                
                // Attempt to restart the Limelight polling thread
                try {
                    ioScope.launch {
                        try {
                            limelight.start()
                            System.out.println("FtcLimelightIO: Attempted to restart Limelight driver streaming.")
                        } catch (ex: Throwable) {
                            System.err.println("FtcLimelightIO: Failed to restart Limelight driver.")
                        }
                    }
                } catch (ex: Throwable) {
                    System.err.println("FtcLimelightIO: Failed to launch restart coroutine.")
                }
            }
        }
    }

    override fun close() {
        ioScope.cancel()
    }
}

