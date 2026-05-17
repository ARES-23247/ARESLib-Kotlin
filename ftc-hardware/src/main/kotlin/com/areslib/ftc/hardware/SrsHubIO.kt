package com.areslib.ftc.hardware

import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice
import com.qualcomm.robotcore.hardware.I2cDeviceSynch
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties
import com.qualcomm.robotcore.hardware.AnalogInput
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
    // Structured bulk cache buffers
    private val cachedAnalog = DoubleArray(4)
    private val cachedDigital = BooleanArray(4)
    private val cachedEncoders = IntArray(4)
    
    // I2C Sub-sensors cached data
    private val cachedVL53L5CX = Array(4) { IntArray(64) }
    
    // APDS9151 and VL53L0X cache (ports 0-3)
    private val cachedColorsRed = IntArray(4)
    private val cachedColorsGreen = IntArray(4)
    private val cachedColorsBlue = IntArray(4)
    private val cachedColorsAlpha = IntArray(4)
    private val cachedI2cDistances = DoubleArray(4)
    
    // Pinpoint Odometry cache (ports 0-3)
    private val cachedOdoX = DoubleArray(4)
    private val cachedOdoY = DoubleArray(4)
    private val cachedOdoHeading = DoubleArray(4)
    private val cachedOdoVelX = DoubleArray(4)
    private val cachedOdoVelY = DoubleArray(4)
    private val cachedOdoHeadingVel = DoubleArray(4)

    override fun doInitialize(): Boolean {
        // Set up automatic repeated read window for the entire 256-byte register range
        deviceClient.readWindow = I2cDeviceSynch.ReadWindow(0, 256, I2cDeviceSynch.ReadMode.REPEAT)
        return true
    }

    override fun getManufacturer(): Manufacturer = Manufacturer.Other

    override fun getDeviceName(): String = "SRS Hub"

    /**
     * Polls the SRS Hub exactly once, fetching the entire integrated register space
     * containing analog, digital, encoders, and I2C sub-device telemetry in a single transaction.
     * MUST be called once at the start of your loop.
     */
    @Synchronized
    fun update() {
        val data = deviceClient.read(0, 256)
        if (data.size < 256) return // Safeguard against incomplete read

        // 1. Parse Analog Input Voltages (Registers 0-7, 2 bytes per port)
        for (i in 0 until 4) {
            val raw = (data[i * 2].toInt() and 0xFF) or ((data[i * 2 + 1].toInt() and 0xFF) shl 8)
            cachedAnalog[i] = (raw / 65535.0) * 3.3
        }

        // 2. Parse Digital States (Register 8)
        val digitalByte = data[8].toInt()
        for (i in 0 until 4) {
            cachedDigital[i] = (digitalByte and (1 shl i)) != 0
        }

        // 3. Parse Motor Encoders (Registers 9-24, 4 bytes per port)
        for (i in 0 until 4) {
            val offset = 9 + i * 4
            cachedEncoders[i] = readInt32(data, offset)
        }

        // 4. Parse APDS9151 and VL53L0X I2C Sub-devices (Registers 32-95, 16 bytes per port)
        for (port in 0 until 4) {
            val base = 32 + port * 16
            cachedColorsRed[port] = (data[base].toInt() and 0xFF) or ((data[base + 1].toInt() and 0xFF) shl 8)
            cachedColorsGreen[port] = (data[base + 2].toInt() and 0xFF) or ((data[base + 3].toInt() and 0xFF) shl 8)
            cachedColorsBlue[port] = (data[base + 4].toInt() and 0xFF) or ((data[base + 5].toInt() and 0xFF) shl 8)
            cachedColorsAlpha[port] = (data[base + 6].toInt() and 0xFF) or ((data[base + 7].toInt() and 0xFF) shl 8)
            
            val rawDistMm = (data[base + 8].toInt() and 0xFF) or ((data[base + 9].toInt() and 0xFF) shl 8)
            cachedI2cDistances[port] = rawDistMm / 1000.0 // mm to meters
        }

        // 5. Parse Pinpoint Odometry data (Registers 96-191, 24 bytes per port)
        for (port in 0 until 4) {
            val base = 96 + port * 24
            cachedOdoX[port] = readInt32(data, base).toDouble()
            cachedOdoY[port] = readInt32(data, base + 4).toDouble()
            
            val rawHeading = readInt32(data, base + 8)
            cachedOdoHeading[port] = rawHeading / 1e6 // microradians to radians
            
            cachedOdoVelX[port] = readInt32(data, base + 12).toDouble()
            cachedOdoVelY[port] = readInt32(data, base + 16).toDouble()
            
            val rawHeadingVel = readInt32(data, base + 20)
            cachedOdoHeadingVel[port] = rawHeadingVel / 1e6
        }

        // 6. Parse VL53L5CX Multizone distance data (Registers 192-255, 64 zones of 2-byte values mapped dynamically)
        val vl53Base = 192
        for (i in 0 until 32) { // Retrieve standard 32 zones in single bulk block
            val offset = vl53Base + (i * 2)
            if (offset + 1 < data.size) {
                val mm = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                cachedVL53L5CX[0][i] = mm
            }
        }
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        if (offset + 3 >= data.size) return 0
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    // Now, all accessor methods return cached data instantly in 0.0ms!
    @Synchronized fun getAnalogVoltage(port: Int): Double = cachedAnalog.getOrElse(port) { 0.0 }
    @Synchronized fun getDigitalState(port: Int): Boolean = cachedDigital.getOrElse(port) { false }
    @Synchronized fun readEncoder(port: Int): Int = cachedEncoders.getOrElse(port) { 0 }
    
    @Synchronized fun getVL53L5CXDistances(port: Int): IntArray = cachedVL53L5CX.getOrElse(port) { IntArray(64) }
    @Synchronized fun getI2cColorRed(port: Int): Int = cachedColorsRed.getOrElse(port) { 0 }
    @Synchronized fun getI2cColorGreen(port: Int): Int = cachedColorsGreen.getOrElse(port) { 0 }
    @Synchronized fun getI2cColorBlue(port: Int): Int = cachedColorsBlue.getOrElse(port) { 0 }
    @Synchronized fun getI2cColorAlpha(port: Int): Int = cachedColorsAlpha.getOrElse(port) { 0 }
    @Synchronized fun getI2cDistanceMeters(port: Int): Double = cachedI2cDistances.getOrElse(port) { 0.0 }

    @Synchronized fun getI2cOdometryX(port: Int): Double = cachedOdoX.getOrElse(port) { 0.0 }
    @Synchronized fun getI2cOdometryY(port: Int): Double = cachedOdoY.getOrElse(port) { 0.0 }
    @Synchronized fun getI2cOdometryHeading(port: Int): Double = cachedOdoHeading.getOrElse(port) { 0.0 }
    @Synchronized fun getI2cOdometryVelX(port: Int): Double = cachedOdoVelX.getOrElse(port) { 0.0 }
    @Synchronized fun getI2cOdometryVelY(port: Int): Double = cachedOdoVelY.getOrElse(port) { 0.0 }
    @Synchronized fun getI2cOdometryHeadingVel(port: Int): Double = cachedOdoHeadingVel.getOrElse(port) { 0.0 }

    // Direct actuator writes (these bypass the read cache and run immediately)
    @Synchronized
    fun setPwmDutyCycle(port: Int, dutyCycle: Double) {
        val raw = (dutyCycle.coerceIn(0.0, 1.0) * 65535.0).toInt()
        val buffer = byteArrayOf((raw and 0xFF).toByte(), ((raw shl 8) and 0xFF).toByte())
        deviceClient.write(16 + port * 2, buffer)
    }

    @Synchronized
    fun readPwmPulseWidth(port: Int): Int {
        val data = deviceClient.read(24 + port * 2, 2)
        return (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
    }

    @Synchronized
    fun resetI2cOdometry(port: Int) {
        deviceClient.write(120 + port, byteArrayOf(1))
    }

    @Synchronized
    fun updateI2cOdometry(port: Int) {
        deviceClient.write(124 + port, byteArrayOf(1))
    }
}

class SrsHubAnalogIO(private val srsHub: SrsHubDriver, private val port: Int) : AnalogInput {
    override val voltage: Double
        get() = srsHub.getAnalogVoltage(port)
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
