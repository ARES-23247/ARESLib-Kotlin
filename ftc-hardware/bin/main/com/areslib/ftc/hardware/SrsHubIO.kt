package com.areslib.ftc.hardware

import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice
import com.qualcomm.robotcore.hardware.I2cDeviceSynch
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType
import com.areslib.hardware.MotorIO
import com.areslib.hardware.ServoIO

@I2cDeviceType
@DeviceProperties(
    name = "SRS Hub",
    xmlTag = "SrsHub",
    description = "SRS Robotics Expansion Hub over I2C"
)
class SrsHubDriver(deviceClient: I2cDeviceSynch) : I2cDeviceSynchDevice<I2cDeviceSynch>(deviceClient, true) {
    override fun doInitialize(): Boolean {
        // Dummy initialization for SRS Hub
        return true
    }

    override fun getManufacturer(): Manufacturer = Manufacturer.Other

    override fun getDeviceName(): String = "SRS Hub"

    fun getAnalogVoltage(port: Int): Double {
        return 0.0 // Read from I2C registers in real driver
    }

    fun getDigitalState(port: Int): Boolean {
        return false // Read from I2C registers in real driver
    }

    fun setPwmDutyCycle(port: Int, dutyCycle: Double) {
        // Write to I2C registers in real driver
    }

    fun readPwmPulseWidth(port: Int): Int {
        return 0 // Read from I2C registers in real driver
    }

    fun readEncoder(port: Int): Int {
        return 0 // Read from I2C registers in real driver
    }
}

class SrsHubAnalogIO(private val srsHub: SrsHubDriver, private val port: Int) {
    fun getVoltage(): Double {
        return srsHub.getAnalogVoltage(port)
    }
}

class SrsHubDigitalIO(private val srsHub: SrsHubDriver, private val port: Int) {
    fun getState(): Boolean {
        return srsHub.getDigitalState(port)
    }
}

class SrsHubServoIO(private val srsHub: SrsHubDriver, private val port: Int) : ServoIO {
    private var currentTarget = 0.0

    override var position: Double
        get() = currentTarget
        set(value) {
            currentTarget = value
            srsHub.setPwmDutyCycle(port, value) // Very simplified
        }
}

class SrsHubEncoderIO(private val srsHub: SrsHubDriver, private val port: Int) : MotorIO {
    override var power: Double
        get() = 0.0
        set(value) {}

    override val velocity: Double
        get() = 0.0

    override val position: Double
        get() = srsHub.readEncoder(port).toDouble()

    override fun resetEncoder() {}
}

/**
 * Wrapper for an absolute analog encoder plugged into the SRS Hub.
 */
class SrsHubAbsoluteAnalogEncoder(
    private val srsHub: SrsHubDriver,
    private val port: Int,
    private val version: com.areslib.hardware.RevEncoderVersion = com.areslib.hardware.RevEncoderVersion.V1,
    private val ticksPerRev: Double = 8192.0
) : MotorIO {
    private var offset = 0.0

    override var power: Double
        get() = 0.0
        set(value) {}

    override val velocity: Double
        get() = 0.0

    override val position: Double
        get() {
            val normalized = srsHub.getAnalogVoltage(port) / version.maxVoltage
            return (normalized * ticksPerRev) - offset
        }

    override fun resetEncoder() {
        offset = (srsHub.getAnalogVoltage(port) / version.maxVoltage) * ticksPerRev
    }
}

/**
 * Wrapper for an absolute PWM encoder plugged into the SRS Hub.
 */
class SrsHubAbsolutePWMEncoder(
    private val srsHub: SrsHubDriver,
    private val port: Int,
    private val version: com.areslib.hardware.RevEncoderVersion = com.areslib.hardware.RevEncoderVersion.V1,
    private val ticksPerRev: Double = 8192.0
) : MotorIO {
    private var offset = 0.0

    override var power: Double
        get() = 0.0
        set(value) {}

    override val velocity: Double
        get() = 0.0

    override val position: Double
        get() {
            val pulseUs = srsHub.readPwmPulseWidth(port).toDouble()
            val normalized = (pulseUs - version.minPulseUs) / (version.maxPulseUs - version.minPulseUs)
            return (normalized.coerceIn(0.0, 1.0) * ticksPerRev) - offset
        }

    override fun resetEncoder() {
        val pulseUs = srsHub.readPwmPulseWidth(port).toDouble()
        val normalized = (pulseUs - version.minPulseUs) / (version.maxPulseUs - version.minPulseUs)
        offset = normalized.coerceIn(0.0, 1.0) * ticksPerRev
    }
}
