package com.qualcomm.robotcore.hardware

/**
 * Mock representation of an FTC [HardwareDevice].
 */
interface HardwareDevice {
    fun getManufacturer(): Manufacturer = Manufacturer.Unknown
    fun getDeviceName(): String = "MockDevice"
    fun getConnectionInfo(): String = "MockConnection"
    fun getVersion(): Int = 1
    fun resetDeviceConfigurationForOpMode() {}
    fun close() {}

    enum class Manufacturer {
        Unknown, Other, Lego, HiTechnic, ModernRobotics, Adafruit, Matrix,
        Lynx, AMS, STMicroelectronics, StepperMotor, I2cDeviceSynchImplSimple,
        I2cDeviceSynchImpl, Broadcom, MaxBotix, GoBilda, Rev
    }
}

/**
 * Mock representation of an FTC [HardwareMap].
 */
open class HardwareMap {
    open fun <T> get(classOrType: Class<out T>, deviceName: String): T {
        throw NotImplementedError("Mock HardwareMap.get() not overridden")
    }
    open fun <T> getAll(classOrType: Class<out T>): List<T> {
        throw NotImplementedError("Mock HardwareMap.getAll() not overridden")
    }
    open fun <T> getNamesOf(device: T): Set<String> {
        return emptySet()
    }
    open fun remove(serialNumber: String, deviceName: String): Boolean {
        return true
    }
    open fun remove(serialNumber: com.qualcomm.robotcore.util.SerialNumber, deviceName: String): Boolean {
        return true
    }
    open fun remove(serialNumber: String, device: HardwareDevice): Boolean {
        return true
    }
    open fun remove(serialNumber: com.qualcomm.robotcore.util.SerialNumber, device: HardwareDevice): Boolean {
        return true
    }
    open fun put(serialNumber: String, deviceName: String, device: HardwareDevice) {}
    open fun put(serialNumber: com.qualcomm.robotcore.util.SerialNumber, deviceName: String, device: HardwareDevice) {}
    open fun put(deviceName: String, device: HardwareDevice) {}
}

/**
 * Mock representation of an FTC [VoltageSensor].
 */
interface VoltageSensor : HardwareDevice {
    val voltage: Double
}
