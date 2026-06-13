package com.areslib.frc

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.state.VisionMeasurement
import com.areslib.math.Pose3d
import com.areslib.math.Translation3d
import com.areslib.math.Rotation3d
import edu.wpi.first.networktables.NetworkTableInstance

/**
 * FRC-specific implementation of the VisionIO abstraction for Limelight.
 * Reads 3D field-relative robot pose data directly from NetworkTables (NT4).
 */
class FrcLimelightIO(
    val tableName: String = "limelight",
    override val cameraPoses: List<Pose3d> = listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)))
) : VisionIO {

    private val table = NetworkTableInstance.getDefault().getTable(tableName)
    private val botposeSub = table.getDoubleArrayTopic("botpose_wpiblue").subscribe(DoubleArray(0))
    private val tvSub = table.getIntegerTopic("tv").subscribe(0)

    override fun updateInputs(inputs: VisionIOInputs) {
        inputs.cameraPoses = cameraPoses
        
        val botpose = botposeSub.get()
        val tv = tvSub.get()
        
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
            
            val pose = Pose3d(
                translation = Translation3d(x, y, z),
                rotation = Rotation3d(roll, pitch, yaw)
            )
            
            inputs.measurements = listOf(
                VisionMeasurement(
                    timestampMs = timestampMs,
                    targetPose = pose,
                    tagId = -1,
                    ambiguity = ambiguity
                )
            )
        } else {
            inputs.measurements = emptyList()
        }
    }
}
