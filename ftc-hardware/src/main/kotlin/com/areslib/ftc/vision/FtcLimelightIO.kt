package com.areslib.ftc.vision

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.geometry.Pose3d
import com.areslib.math.geometry.Translation3d
import com.areslib.math.geometry.Rotation3d
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
    private val frameRotation = com.areslib.math.geometry.Rotation3d(0.0, 0.0, -Math.PI / 2.0)
    
    // Object pools to prevent GC overhead
    private val visionMeasurementPool = Array(10) { VisionMeasurement() }
    private val translationPool = Array(20) { Translation3d() }
    private val rotationPool = Array(20) { Rotation3d() }
    private val posePool = Array(20) { Pose3d() }
    private var measurementPoolIndex = 0
    private var translationPoolIndex = 0
    private var rotationPoolIndex = 0
    private var posePoolIndex = 0

    private val scratchMeasurements = ArrayList<VisionMeasurement>(10)
    private val measurementListPool = Array(10) { ArrayList<VisionMeasurement>(10) }
    private var measurementListPoolIndex = 0

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
            val botpose = result?.getBotpose()
            
            inputs.isConnected = result != null && result.isValid()
            
            when {
                result == null || !result.isValid() -> {
                    inputs.measurements = emptyList()
                }
                botpose == null -> {
                    inputs.measurements = emptyList()
                }
                else -> {
                    val pos = botpose.position.toUnit(DistanceUnit.METER)
                    val orient = botpose.orientation
                    
                    val wpiTranslation = translationPool[translationPoolIndex].apply {
                        x = pos.x
                        y = pos.y
                        z = pos.z
                    }
                    translationPoolIndex = (translationPoolIndex + 1) % 20
                    
                    val wpiRotation = rotationPool[rotationPoolIndex].apply {
                        setEulerAngles(
                            orient.getRoll(AngleUnit.RADIANS),
                            orient.getPitch(AngleUnit.RADIANS),
                            orient.getYaw(AngleUnit.RADIANS)
                        )
                    }
                    rotationPoolIndex = (rotationPoolIndex + 1) % 20
                    
                    val pose = posePool[posePoolIndex].apply {
                        translation = wpiTranslation
                        rotation = wpiRotation
                    }
                    posePoolIndex = (posePoolIndex + 1) % 20
                    
                    val fiducials = result.getFiducialResults()
                    when {
                        fiducials.isNotEmpty() -> {
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
                                val targetTranslation = translationPool[translationPoolIndex].apply {
                                    x = posTargetMeters.x
                                    y = posTargetMeters.y
                                    z = posTargetMeters.z
                                }
                                translationPoolIndex = (translationPoolIndex + 1) % 20
                                
                                val targetRotation = rotationPool[rotationPoolIndex].apply {
                                    setEulerAngles(
                                        orientTarget.getRoll(AngleUnit.RADIANS),
                                        orientTarget.getPitch(AngleUnit.RADIANS),
                                        orientTarget.getYaw(AngleUnit.RADIANS)
                                    )
                                }
                                rotationPoolIndex = (rotationPoolIndex + 1) % 20
                                
                                val robotPoseTargetSpaceWpi = posePool[posePoolIndex].apply {
                                    translation = targetTranslation
                                    rotation = targetRotation
                                }
                                posePoolIndex = (posePoolIndex + 1) % 20
                                
                                val distance = kotlin.math.hypot(posTargetMeters.x, posTargetMeters.z)
                                val estAmbiguity = if (distance > 0.0) (0.02 * (distance * distance)).coerceAtMost(0.99) else 0.0
    
                                val measurement = visionMeasurementPool[measurementPoolIndex].apply {
                                    timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
                                    targetPose = pose
                                    tagId = f.getFiducialId()
                                    ambiguity = estAmbiguity
                                    robotPoseTargetSpace = robotPoseTargetSpaceWpi
                                }
                                measurementPoolIndex = (measurementPoolIndex + 1) % 10
                                scratchMeasurements.add(measurement)
                            }
                            // Copy to avoid mutating the list in the Redux state
                            val safeMeasurements = measurementListPool[measurementListPoolIndex]
                            safeMeasurements.clear()
                            for (i in 0 until scratchMeasurements.size) safeMeasurements.add(scratchMeasurements[i])
                            measurementListPoolIndex = (measurementListPoolIndex + 1) % 10
                            inputs.measurements = safeMeasurements
                        }
                        else -> {
                            val measurement = visionMeasurementPool[measurementPoolIndex].apply {
                                timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
                                targetPose = pose
                                tagId = -1
                                ambiguity = 0.0
                            }
                            measurementPoolIndex = (measurementPoolIndex + 1) % 10
                            
                            val safeMeasurements = measurementListPool[measurementListPoolIndex]
                            safeMeasurements.clear()
                            safeMeasurements.add(measurement)
                            measurementListPoolIndex = (measurementListPoolIndex + 1) % 10
                            inputs.measurements = safeMeasurements
                        }
                    }
                }
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

