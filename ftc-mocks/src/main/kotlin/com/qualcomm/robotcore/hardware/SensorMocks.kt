package com.qualcomm.robotcore.hardware

/**
 * Mock representation of an FTC [DistanceSensor].
 */
interface DistanceSensor : HardwareDevice {
    /**
     * getDistance declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getDistance(unit: org.firstinspires.ftc.robotcore.external.navigation.Distance): Double
}

/**
 * Mock representation of an FTC [ColorSensor].
 */
interface ColorSensor : HardwareDevice {
    /**
     * red declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun red(): Int
    /**
     * green declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun green(): Int
    /**
     * blue declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun blue(): Int
    /**
     * alpha declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun alpha(): Int
}

/**
 * Mock representation of an FTC [NormalizedRGBA].
 */
class NormalizedRGBA {
    @JvmField var red: Float = 0f
    @JvmField var green: Float = 0f
    @JvmField var blue: Float = 0f
    @JvmField var alpha: Float = 0f
}

/**
 * Mock representation of an FTC [NormalizedColorSensor].
 */
interface NormalizedColorSensor : HardwareDevice {
    val normalizedColors: NormalizedRGBA
}

/**
 * Mock representation of an FTC [AnalogInput].
 */
open class AnalogInput : HardwareDevice {
    open val voltage: Double = 0.0
    /**
     * getManufacturer declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getManufacturer(): HardwareDevice.Manufacturer = HardwareDevice.Manufacturer.Unknown
    /**
     * getDeviceName declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getDeviceName(): String = "AnalogInput"
    /**
     * getConnectionInfo declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getConnectionInfo(): String = ""
    /**
     * getVersion declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getVersion(): Int = 1
    /**
     * resetDeviceConfigurationForOpMode declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun resetDeviceConfigurationForOpMode() {}
    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {}
}

/**
 * Mock representation of an FTC [DigitalChannel].
 */
interface DigitalChannel : HardwareDevice {
    /**
     * Mode declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class Mode { INPUT, OUTPUT }
    var mode: Mode
    val state: Boolean
}

/**
 * Mock representation of an FTC [IMU].
 */
interface IMU : HardwareDevice {
    /**
     * Parameters declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    class Parameters(val hubOrientationOnRobot: com.qualcomm.hardware.rev.RevHubOrientationOnRobot)

    /**
     * initialize declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun initialize(parameters: Parameters): Boolean
    /**
     * resetYaw declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun resetYaw()
    /**
     * getRobotYawPitchRollAngles declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getRobotYawPitchRollAngles(): org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles
    /**
     * getRobotAngularVelocity declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getRobotAngularVelocity(unit: org.firstinspires.ftc.robotcore.external.navigation.AngleUnit): org.firstinspires.ftc.robotcore.external.navigation.AngularVelocity
}
