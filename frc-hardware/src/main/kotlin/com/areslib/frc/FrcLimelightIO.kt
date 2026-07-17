package com.areslib.frc

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.geometry.Pose3d
import com.areslib.math.geometry.Translation3d
import com.areslib.math.geometry.Rotation3d
import edu.wpi.first.networktables.NetworkTableInstance

/**
 * FRC-specific implementation of the VisionIO abstraction for Limelight.
 * Reads 3D field-relative robot pose data directly from NetworkTables (NT4).
 */
class FrcLimelightIO(
    val tableName: String = "limelight",
    override val cameraPoses: List<Pose3d> = listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0))),
    val defaultPipeline: Int = 0,
    val imuMode: Int = 4 // 4 = INTERNAL_EXTERNAL_ASSIST (recommended for LL3G/LL4), 0 = EXTERNAL_ONLY
) : VisionIO {

    private val table = NetworkTableInstance.getDefault().getTable(tableName)
    private val botposeSub = table.getDoubleArrayTopic("botpose_wpiblue").subscribe(DoubleArray(0))
    private val botposeMt2Sub = table.getDoubleArrayTopic("botpose_wpiblue_mt2").subscribe(DoubleArray(0))
    private val tvSub = table.getIntegerTopic("tv").subscribe(0)
    private val botposeTargetSpaceSub = table.getDoubleArrayTopic("botpose_targetspace").subscribe(DoubleArray(0))
    private val tidSub = table.getIntegerTopic("tid").subscribe(-1)
    
    private val orientationPub = table.getDoubleArrayTopic("orientation_megatag2").publish()

    private var lastLinearVelocityMps = 0.0
    private var lastYawRateDegPerSec = 0.0
    
    // Pre-allocated buffers to prevent GC
    private val scratchBotpose = DoubleArray(7)
    private val scratchOrientation = DoubleArray(6)
    
    // Single pre-allocated instance for Zero-GC
    private val cachedMeasurement = VisionMeasurement()
    private val cachedMeasurementList = java.util.Collections.singletonList(cachedMeasurement)

    init {
        // Enforce match-ready settings to NetworkTables on startup
        try {
            table.getIntegerTopic("pipeline").publish().set(defaultPipeline.toLong())
            table.getIntegerTopic("ledMode").publish().set(1L) // 1 = Force Off
            table.getIntegerTopic("stream").publish().set(0L)  // 0 = Standard Stream
            table.getIntegerTopic("imuMode").publish().set(imuMode.toLong())
        } catch (e: Exception) {
            System.err.println("FrcLimelightIO: Failed to write startup configuration: ${e.message}")
        }
    }

    override fun setOrientation(
        yawDegrees: Double, yawRateDegPerSec: Double,
        pitchDegrees: Double, pitchRateDegPerSec: Double,
        rollDegrees: Double, rollRateDegPerSec: Double,
        linearVelocityMps: Double
    ) {
        lastLinearVelocityMps = linearVelocityMps
        lastYawRateDegPerSec = yawRateDegPerSec
        
        scratchOrientation[0] = yawDegrees
        scratchOrientation[1] = yawRateDegPerSec
        scratchOrientation[2] = pitchDegrees
        scratchOrientation[3] = pitchRateDegPerSec
        scratchOrientation[4] = rollDegrees
        scratchOrientation[5] = rollRateDegPerSec
        
        orientationPub.set(scratchOrientation)
    }

    override fun updateInputs(inputs: VisionIOInputs) {
        inputs.cameraPoses = cameraPoses
        
        val tv = tvSub.get()
        val isStatic = lastLinearVelocityMps < 0.15 && Math.abs(lastYawRateDegPerSec) < 10.0
        
        // Select topic based on robot motion state (Hybrid MegaTag strategy)
        // - MegaTag1 (botpose_wpiblue) when static to allow EKF to correct yaw drift.
        // - MegaTag2 (botpose_wpiblue_mt2) when moving to prevent pose-flipping.
        val botpose = if (isStatic) {
            val mt1 = botposeSub.get()
            if (mt1.isNotEmpty()) mt1 else botposeMt2Sub.get()
        } else {
            val mt2 = botposeMt2Sub.get()
            if (mt2.isNotEmpty()) mt2 else botposeSub.get()
        }
        
        inputs.isConnected = tv == 1L && botpose.isNotEmpty()
        
        if (inputs.isConnected && botpose.size >= 6) {
            val x = botpose[0]
            val y = botpose[1]
            val z = botpose[2]
            val roll = Math.toRadians(botpose[3])
            val pitch = Math.toRadians(botpose[4])
            val yaw = Math.toRadians(botpose[5])
            
            // Limelight latency (ms) is typically index 6.
            val latencyMs = if (botpose.size > 6) botpose[6] else 0.0
            val timestampMs = com.areslib.util.RobotClock.currentTimeMillis() - latencyMs.toLong()
            
            // Limelight's botpose_wpiblue array does not contain single-tag ambiguity.
            // Index 10 represents Average Target Area (percent), which is typically > 0.15% 
            // for good close-up tag readings, causing false outlier rejects. We set ambiguity 
            // to a stable constant (0.02) as multitag pose estimations are extremely stable.
            val ambiguity = 0.02
            
            // Update the pre-allocated cached object
            cachedMeasurement.timestampMs = timestampMs
            cachedMeasurement.targetPose.translation.x = x
            cachedMeasurement.targetPose.translation.y = y
            cachedMeasurement.targetPose.translation.z = z
            
            // Set rotation
            val cr = Math.cos(roll * 0.5)
            val sr = Math.sin(roll * 0.5)
            val cp = Math.cos(pitch * 0.5)
            val sp = Math.sin(pitch * 0.5)
            val cy = Math.cos(yaw * 0.5)
            val sy = Math.sin(yaw * 0.5)

            cachedMeasurement.targetPose.rotation.q.w = cr * cp * cy + sr * sp * sy
            cachedMeasurement.targetPose.rotation.q.x = sr * cp * cy - cr * sp * sy
            cachedMeasurement.targetPose.rotation.q.y = cr * sp * cy + sr * cp * sy
            cachedMeasurement.targetPose.rotation.q.z = cr * cp * sy - sr * sp * cy
            
            cachedMeasurement.tagId = -1
            cachedMeasurement.ambiguity = ambiguity
            
            // Populate target-space pose for alignment controllers
            val targetSpace = botposeTargetSpaceSub.get()
            if (targetSpace.size >= 6) {
                cachedMeasurement.robotPoseTargetSpace.translation.x = targetSpace[0]
                cachedMeasurement.robotPoseTargetSpace.translation.y = targetSpace[1]
                cachedMeasurement.robotPoseTargetSpace.translation.z = targetSpace[2]
                // Rotation3d stores a quaternion; x/y/z are read-only Euler getters.
                // Construct from Euler angles and copy the quaternion to avoid allocation.
                val tsRot = Rotation3d(Math.toRadians(targetSpace[3]), Math.toRadians(targetSpace[4]), Math.toRadians(targetSpace[5]))
                cachedMeasurement.robotPoseTargetSpace.rotation.q.w = tsRot.q.w
                cachedMeasurement.robotPoseTargetSpace.rotation.q.x = tsRot.q.x
                cachedMeasurement.robotPoseTargetSpace.rotation.q.y = tsRot.q.y
                cachedMeasurement.robotPoseTargetSpace.rotation.q.z = tsRot.q.z
            }
            cachedMeasurement.tagId = tidSub.get().toInt()
            
            // Zero-GC list wrapper
            inputs.measurements = cachedMeasurementList
        } else {
            inputs.measurements = emptyList()
        }
    }
}

