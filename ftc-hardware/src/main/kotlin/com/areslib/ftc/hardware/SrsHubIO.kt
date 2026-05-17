package com.areslib.ftc.hardware

import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice
import com.qualcomm.robotcore.hardware.I2cDeviceSynch
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType
import com.areslib.hardware.MotorIO
import com.areslib.hardware.ServoIO
import com.areslib.hardware.ColorSensorIO
import com.areslib.hardware.DistanceSensorIO
import com.areslib.hardware.MultizoneDistanceSensorIO
import com.areslib.hardware.OdometryIO
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d

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

    // --- I2C Sensor Support (e.g. APDS9151, VL53L5CX, VL53L0X, Pinpoint) ---

    fun getVL53L5CXDistances(port: Int): IntArray {
        // Read 8x8 matrix (64 zones in mm) from the VL53L5CX connected to the SRS Hub I2C port
        return IntArray(64) { 0 }
    }

    fun getI2cColorRed(port: Int): Int = 0
    fun getI2cColorGreen(port: Int): Int = 0
    fun getI2cColorBlue(port: Int): Int = 0
    fun getI2cColorAlpha(port: Int): Int = 0
    fun getI2cDistanceMeters(port: Int): Double = 0.0

    fun resetI2cOdometry(port: Int) {}
    fun updateI2cOdometry(port: Int) {}
    fun getI2cOdometryX(port: Int): Double = 0.0
    fun getI2cOdometryY(port: Int): Double = 0.0
    fun getI2cOdometryHeading(port: Int): Double = 0.0
    fun getI2cOdometryVelX(port: Int): Double = 0.0
    fun getI2cOdometryVelY(port: Int): Double = 0.0
    fun getI2cOdometryHeadingVel(port: Int): Double = 0.0
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

// ==========================================
// =         SRS HUB I2C SENSORS            =
// ==========================================

/**
 * Wrapper for a VL53L5CX multizone ToF sensor plugged into an I2C expansion port of the SRS Hub.
 */
class SrsHubVL53L5CX(
    private val srsHub: SrsHubDriver,
    private val port: Int,
    override val rows: Int = 8,
    override val columns: Int = 8
) : MultizoneDistanceSensorIO {
    override val distancesMeters: DoubleArray
        get() {
            val raw = srsHub.getVL53L5CXDistances(port)
            val converted = DoubleArray(raw.size)
            for (i in raw.indices) {
                converted[i] = raw[i] / 1000.0 // Convert mm to meters
            }
            return converted
        }
}

/**
 * Wrapper for an APDS9151 (REV Color Sensor V3) plugged into an I2C expansion port of the SRS Hub.
 */
class SrsHubRevColorSensorV3(
    private val srsHub: SrsHubDriver,
    private val port: Int
) : ColorSensorIO, DistanceSensorIO {
    override val red: Int
        get() = srsHub.getI2cColorRed(port)
    override val green: Int
        get() = srsHub.getI2cColorGreen(port)
    override val blue: Int
        get() = srsHub.getI2cColorBlue(port)
    override val alpha: Int
        get() = srsHub.getI2cColorAlpha(port)

    override val normalizedRgb: DoubleArray
        get() {
            val sum = (red + green + blue + alpha).toDouble()
            if (sum < 0.1) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            return doubleArrayOf(red / sum, green / sum, blue / sum, alpha / sum)
        }

    override val distanceMeters: Double
        get() = srsHub.getI2cDistanceMeters(port)
}

/**
 * Wrapper for a VL53L0X (REV 2M Distance / Adafruit ToF) plugged into an I2C expansion port of the SRS Hub.
 */
class SrsHubVL53L0X(
    private val srsHub: SrsHubDriver,
    private val port: Int
) : DistanceSensorIO {
    override val distanceMeters: Double
        get() = srsHub.getI2cDistanceMeters(port)
}

/**
 * Wrapper for a GoBilda Pinpoint Odometry Computer plugged into an I2C expansion port of the SRS Hub.
 */
class SrsHubPinpointOdometry(
    private val srsHub: SrsHubDriver,
    private val port: Int
) : OdometryIO {
    override fun initialize(startPose: Pose2d) {
        srsHub.resetI2cOdometry(port)
    }

    override fun update() {
        srsHub.updateI2cOdometry(port)
    }

    override val position: Pose2d
        get() = Pose2d(
            srsHub.getI2cOdometryX(port) / 1000.0,
            srsHub.getI2cOdometryY(port) / 1000.0,
            Rotation2d(srsHub.getI2cOdometryHeading(port))
        )

    override val velocity: Pose2d
        get() = Pose2d(
            srsHub.getI2cOdometryVelX(port) / 1000.0,
            srsHub.getI2cOdometryVelY(port) / 1000.0,
            Rotation2d(srsHub.getI2cOdometryHeadingVel(port))
        )
}
