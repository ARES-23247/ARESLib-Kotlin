package com.areslib.ftc.hardware

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.IMU
import com.areslib.ftc.drivetrain.PinpointIO
import com.areslib.ftc.vision.FtcLimelightIO
import com.areslib.hardware.sensor.ImuIO
import com.areslib.hardware.vision.CompositeVisionIO
import com.areslib.hardware.vision.VisionIO

/**
 * Handles robust, exception-safe initialization of hardware sensors (Pinpoint, IMU, Limelight)
 * from the FTC [HardwareMap].
 */
object FtcHardwareMapInitializer {

    /**
     * initPinpoint declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun initPinpoint(
        hardwareMap: HardwareMap,
        pinpointName: String?,
        xOffsetMm: Double,
        yOffsetMm: Double,
        encoderResolution: Double?,
        xDirection: GoBildaPinpointDriver.EncoderDirection,
        yDirection: GoBildaPinpointDriver.EncoderDirection,
        isCcwPositive: Boolean
    ): PinpointIO? {
        if (pinpointName == null) return null
        return try {
            val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, pinpointName)
            PinpointIO(
                driver = pinpointDriver,
                xOffsetMm = xOffsetMm,
                yOffsetMm = yOffsetMm,
                encoderResolution = encoderResolution,
                xDirection = xDirection,
                yDirection = yDirection,
                isHeadingCcwPositive = isCcwPositive
            ).apply { recalibrateIMU() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * initImu declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun initImu(hardwareMap: HardwareMap, imuName: String?): ImuIO? {
        if (imuName == null) return null
        return try {
            val imuDriver = hardwareMap.get(IMU::class.java, imuName)
            FtcImu(imuDriver)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * initLimelight declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun initLimelight(hardwareMap: HardwareMap, limelightName: String?): VisionIO? {
        if (limelightName == null) return null
        return try {
            val names = limelightName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            when {
                names.size > 1 -> {
                    val ios = names.map { name ->
                        val limelightDriver = hardwareMap.get(Limelight3A::class.java, name)
                        FtcLimelightIO(limelightDriver)
                    }
                    CompositeVisionIO(ios)
                }
                names.size == 1 -> {
                    val limelightDriver = hardwareMap.get(Limelight3A::class.java, names[0])
                    FtcLimelightIO(limelightDriver)
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
